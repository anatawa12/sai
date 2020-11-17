package com.anatawa12.sai.linker;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Queue;

import com.anatawa12.sai.Callable;
import com.anatawa12.sai.Context;
import com.anatawa12.sai.NativeArray;
import com.anatawa12.sai.Scriptable;
import com.anatawa12.sai.Undefined;
import com.anatawa12.sai.linker.adapters.InterfaceAdapterFactory;
import com.anatawa12.sai.linker.adapters.ListAdapter;
import com.anatawa12.sai.linker.adapters.ScriptableMirror;

final class NashornLinker implements GuardingTypeConverterFactory, ConversionComparator {
    private static final ClassValue<MethodHandle> ARRAY_CONVERTERS = new ClassValue<MethodHandle>() {
        @Override
        protected MethodHandle computeValue(final Class<?> type) {
            return createArrayConverter(type);
        }
    };

    static boolean canLinkTypeStatic(final Class<?> type) {
        return Scriptable.class.isAssignableFrom(type) || Undefined.class == type;
    }

    @Override
    public GuardedTypeConversion convertToType(final Class<?> sourceType, final Class<?> targetType) {
        GuardedInvocation gi = convertToTypeNoCast(sourceType, targetType);
        if(gi != null) {
            return new GuardedTypeConversion(gi.asType(MethodType.methodType(targetType, sourceType)), true);
        }
        gi = getSamTypeConverter(sourceType, targetType);
        if(gi != null) {
            return new GuardedTypeConversion(gi.asType(MethodType.methodType(targetType, sourceType)), false);
        }
        return null;
    }

    private static GuardedInvocation convertToTypeNoCast(final Class<?> sourceType, final Class<?> targetType) {
        final MethodHandle mh = JavaArgumentConverters.getConverter(targetType);
        if (mh != null) {
            return new GuardedInvocation(mh, canLinkTypeStatic(sourceType) ? null : IS_SAI_OR_UNDEFINED_TYPE);
        }

        final GuardedInvocation arrayConverter = getArrayConverter(sourceType, targetType);
        if(arrayConverter != null) {
            return arrayConverter;
        }

        return getMirrorConverter(sourceType, targetType);
    }

    private static GuardedInvocation getSamTypeConverter(final Class<?> sourceType, final Class<?> targetType) {
        // If source type is more generic than ScriptFunction class, we'll need to use a guard
        final boolean isSourceTypeNonGeneric = Callable.class.isAssignableFrom(sourceType);

        if (isAutoConvertibleFromFunction(targetType)) {
            final MethodHandle ctor = InterfaceAdapterFactory.getAdapter(targetType);
            assert ctor != null; // if isAutoConvertibleFromFunction() returned true, then ctor must exist.
            return new GuardedInvocation(ctor, !isSourceTypeNonGeneric ? IS_CALLABLE : null);
        }
        return null;
    }

    private static GuardedInvocation getArrayConverter(final Class<?> sourceType, final Class<?> targetType) {
        final boolean isSourceTypeNativeArray = sourceType == NativeArray.class;

        if (isSourceTypeNativeArray || sourceType.isAssignableFrom(NativeArray.class)) {
            final MethodHandle guard = !isSourceTypeNativeArray ? IS_NATIVE_ARRAY : null;
            if(targetType.isArray()) {
                return new GuardedInvocation(ARRAY_CONVERTERS.get(targetType), guard);
            } else if(targetType == List.class) {
                return new GuardedInvocation(TO_LIST, guard);
            } else if(targetType == Deque.class) {
                return new GuardedInvocation(TO_DEQUE, guard);
            } else if(targetType == Queue.class) {
                return new GuardedInvocation(TO_QUEUE, guard);
            } else if(targetType == Collection.class) {
                return new GuardedInvocation(TO_COLLECTION, guard);
            }
        }
        return null;
    }

    private static MethodHandle createArrayConverter(final Class<?> type) {
        assert type.isArray();
        final MethodHandle converter = MethodHandles.insertArguments(ConvertingHelper.TO_JAVA_ARRAY, 1, type.getComponentType());
        return asReturning(converter, type);
    }

    private static GuardedInvocation getMirrorConverter(final Class<?> sourceType, final Class<?> targetType) {
        if (targetType.isAssignableFrom(ScriptableMirror.class) && targetType != Object.class) {
            if (Scriptable.class.isAssignableFrom(sourceType)) {
                return new GuardedInvocation(CREATE_MIRROR);
            } else {
                return new GuardedInvocation(CREATE_MIRROR, IS_SCRIPTABLE);
            }
        }
        return null;
    }

    private static boolean isAutoConvertibleFromFunction(final Class<?> clazz) {
        return isAbstractClass(clazz) && !Scriptable.class.isAssignableFrom(clazz) &&
                InterfaceAdapterFactory.canConvertAsSam(clazz);
    }

    static boolean isAbstractClass(final Class<?> clazz) {
        return Modifier.isAbstract(clazz.getModifiers()) && !clazz.isArray();
    }


    @Override
    public ConversionComparison compareConversion(final Class<?> sourceType, final Class<?> targetType1, final Class<?> targetType2) {
        if(sourceType == NativeArray.class) {
            // Prefer those types we can convert to with just a wrapper (cheaper than Java array creation).
            if(isArrayPreferredTarget(targetType1)) {
                if(!isArrayPreferredTarget(targetType2)) {
                    return ConversionComparison.TYPE_1_BETTER;
                }
            } else if(isArrayPreferredTarget(targetType2)) {
                return ConversionComparison.TYPE_2_BETTER;
            }
            // Then prefer Java arrays
            if(targetType1.isArray()) {
                if(!targetType2.isArray()) {
                    return ConversionComparison.TYPE_1_BETTER;
                }
            } else if(targetType2.isArray()) {
                return ConversionComparison.TYPE_2_BETTER;
            }
        }
        if(Scriptable.class.isAssignableFrom(sourceType)) {
            // Prefer interfaces
            if(targetType1.isInterface()) {
                if(!targetType2.isInterface()) {
                    return ConversionComparison.TYPE_1_BETTER;
                }
            } else {
                if(targetType2.isInterface()) {
                    return ConversionComparison.TYPE_2_BETTER;
                }
            }
        }
        return ConversionComparison.INDETERMINATE;
    }

    private static boolean isArrayPreferredTarget(final Class<?> clazz) {
        return clazz == List.class || clazz == Collection.class || clazz == Queue.class || clazz == Deque.class;
    }

    private static final MethodHandle IS_SCRIPTABLE = MHH.create1r(Scriptable.class::isInstance);
    private static final MethodHandle IS_CALLABLE = MHH.create1r(Callable.class::isInstance);
    private static final MethodHandle IS_NATIVE_ARRAY = MHH.create1r(NativeArray.class::isInstance);

    private static final MethodHandle IS_SAI_OR_UNDEFINED_TYPE = MHH.create1r(NashornLinker::isSaiOrUndefinedType);
    private static final MethodHandle CREATE_MIRROR = MHH.create1r(NashornLinker::createMirror);

    private static final MethodHandle TO_COLLECTION;
    private static final MethodHandle TO_DEQUE;
    private static final MethodHandle TO_LIST;
    private static final MethodHandle TO_QUEUE;

    static {
        final MethodHandle listAdapterCreate = MHH.create1r(ListAdapter::create);
        TO_COLLECTION = asReturning(listAdapterCreate, Collection.class);
        TO_DEQUE = asReturning(listAdapterCreate, Deque.class);
        TO_LIST = asReturning(listAdapterCreate, List.class);
        TO_QUEUE = asReturning(listAdapterCreate, Queue.class);
    }

    private static MethodHandle asReturning(final MethodHandle mh, final Class<?> nrtype) {
        return mh.asType(mh.type().changeReturnType(nrtype));
    }

    private static boolean isSaiOrUndefinedType(final Object obj) {
        return obj instanceof Scriptable || obj instanceof Undefined;
    }

    private static Object createMirror(final Object obj) {
        return obj instanceof Scriptable? ScriptableMirror.create((Scriptable)obj, Context.getCurrentContext()) : obj;
    }
}

