package com.anatawa12.sai.linker;

import com.anatawa12.sai.Context;
import com.anatawa12.sai.EvaluatorException;
import com.anatawa12.sai.NativeArray;
import com.anatawa12.sai.NativeJavaObject;
import com.anatawa12.sai.ScriptRuntime;
import com.anatawa12.sai.Scriptable;
import com.anatawa12.sai.Undefined;
import com.anatawa12.sai.linker.adapters.InterfaceAdapterFactory;
import com.anatawa12.sai.linker.adapters.ListAdapter;
import com.anatawa12.sai.linker.adapters.ScriptableMirror;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.AbstractMap.SimpleEntry;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;
import javax.script.Bindings;
import jdk.nashorn.api.scripting.AbstractJSObject;
import jdk.nashorn.api.scripting.JSObject;

public class TypeConvertRules {
    public static MethodHandle getConverter(Class<?> source, Class<?> target) {
        MethodHandle handle = getConverterOrNull(source, target);
        return handle == null ? identity.get(source).get(target) : handle;
    }

    public static MethodHandle getConverterOrNull(Class<?> source, Class<?> target) {
        if (dummyForSAM.canUseThisRule(source) && Modifier.isAbstract(target.getModifiers())
                && InterfaceAdapterFactory.canConvertAsSam(target))
            return createSamConverter(source, target);
        return converterOrNull.get(source).get(target);
    }

    private static MethodHandle createSamConverter(Class<?> source, Class<?> target) {
        MethodType type = MethodType.methodType(target, source);
        if (dummyForSAM.staticGuard(source))
            return InterfaceAdapterFactory.getAdapter(target).asType(type);
        return MethodHandles.guardWithTest(dummyForSAM.dynamicGuard.asType(type.changeReturnType(Boolean.TYPE)),
                InterfaceAdapterFactory.getAdapter(target).asType(type),
                identityHandle.asType(type));
    }

    private static final TypeConvertRule dummyForSAM =
            new TypeConvertRule(MHHS.create1r(TypeConvertRules::dummyIdentity), Object.class, Scriptable.class);

    private static final ClassValue<ClassValue<MethodHandle>> identity = new ClassValue<ClassValue<MethodHandle>>() {
        protected ClassValue<MethodHandle> computeValue(final Class<?> source) {
            return new ClassValue<MethodHandle>() {
                protected MethodHandle computeValue(Class<?> target) {
                    return identityHandle.asType(MethodType.methodType(target, source));
                }
            };
        }
    };

    private static final ClassValue<ClassValue<MethodHandle>> converterOrNull = new ClassValue<ClassValue<MethodHandle>>() {
        protected ClassValue<MethodHandle> computeValue(final Class<?> source) {
            return new ClassValue<MethodHandle>() {
                protected MethodHandle computeValue(Class<?> target) {
                    return createConverterOrNull(source, target);
                }
            };
        }
    };
    private static final MethodHandle identityHandle = MethodHandles.identity(Object.class);

    private static final Undefined undefined = (Undefined) Undefined.instance;

    private static MethodHandle createConverterOrNull(Class<?> source, Class<?> target) {
        boolean isFromVoid = source == Void.TYPE;
        if (isFromVoid) {
            source = Undefined.class;
        }

        MethodHandle handle = createConverterInternal(source, target);
        if (handle == null) {
            return null;
        } else {
            return isFromVoid ? MethodHandles.insertArguments(handle, 0, undefined) : handle;
        }
    }

    private static MethodHandle createConverterInternal(Class<?> source, Class<?> target) {
        MethodType type = MethodType.methodType(target, source);
        if (target.isArray() && dummyForNativeArray.canUseThisRule(source)) {
            if (dummyForNativeArray.staticGuard(source))
                return nativeArrayToJavaArray.get(target);
            else
                return guardedNativeArrayToJavaArray.get(target);
        } else {
            List<TypeConvertRule> rules = convertRule.get(source).get(target);
            if (rules.isEmpty())
                return null;

            MethodHandle handle = identityHandle.asType(type);
            for (TypeConvertRule rule : rules) {
                if (rule.staticGuard(source)) {
                    handle = rule.converter.getReal().asType(type);
                } else {
                    handle = MethodHandles.guardWithTest(rule.dynamicGuard.asType(type.changeReturnType(Boolean.TYPE)),
                            rule.converter.getReal().asType(type), handle.asType(type));
                }
            }

            return handle;
        }
    }

    private static final TypeConvertRule dummyForNativeArray =
            new TypeConvertRule(MHHS.create1r(TypeConvertRules::dummyIdentity), Object.class, NativeArray.class);

    private static final ClassValue<ClassValue<List<TypeConvertRule>>> convertRule = new ClassValue<ClassValue<List<TypeConvertRule>>>() {
        protected ClassValue<List<TypeConvertRule>> computeValue(final Class<?> source) {
            return new ClassValue<List<TypeConvertRule>>() {
                protected List<TypeConvertRule> computeValue(Class<?> target) {
                    return createConvertRule(source, target);
                }
            };
        }
    };

    private static List<TypeConvertRule> createConvertRule(Class<?> source, Class<?> target) {
        List<TypeConvertRule> rules;
        if (source.isPrimitive()) {
            rules = factory.primitiveSourceRuleMap.get(new SimpleEntry<Class<?>, Class<?>>(source, target));
            if (rules == null) {
                return Collections.emptyList();
            }
        } else {
            rules = new ArrayList<>();
            List<TypeConvertRule> definedRules = factory.ruleMap.get(target);
            if (definedRules == null) {
                return Collections.emptyList();
            }

            for (TypeConvertRule rule : definedRules) {
                if (rule.canUseThisRule(source)) {
                    if (rule.staticGuard(source)) {
                        rules.clear();
                    }

                    rules.add(rule);
                }
            }
        }

        return rules;
    }

    private static final ClassValue<MethodHandle> nativeArrayToJavaArray = new ClassValue<MethodHandle>() {
        protected MethodHandle computeValue(Class<?> type) {
            return MethodHandles.insertArguments(MHH.create2r(TypeConvertRules::nativeArrayToJavaArray),
                    1, type.getComponentType())
                    .asType(MethodType.methodType(type, Object.class));
        }
    };

    private static final ClassValue<MethodHandle> guardedNativeArrayToJavaArray = new ClassValue<MethodHandle>() {
        protected MethodHandle computeValue(Class<?> type) {
            return MethodHandles.guardWithTest(dummyForNativeArray.dynamicGuard,
                    nativeArrayToJavaArray.get(type),
                    identityHandle.asType(MethodType.methodType(type, Object.class)));
        }
    };

    private static Object dummyIdentity(Object in) {
        return in;
    }

    public static Object nativeArrayToJavaArray(NativeArray arrayIn, Class<?> elementType) {
        if (arrayIn == null) {
            return null;
        } else {
            int length = (int) arrayIn.getLength();
            Object array = Array.newInstance(elementType, length);

            try {
                for (int i = 0; i < length; ++i) {
                    Array.set(array, i, Context.jsToJava(arrayIn.get(i), elementType));
                }
            } catch (EvaluatorException var5) {
                NativeJavaObject.reportConversionError(array, array.getClass());
            }

            return array;
        }
    }

    private static final TypeConverterFactory factory = new TypeConverterFactory();

    static {
        factory.start();
        factory.add(new TypeConvertRule(MHHS.create1r(TypeConvertRules::numberToObject), Object.class, Number.class));
    }

    public static Object numberToObject(Number number) {
        if (number == null) {
            return null;
        } else {
            double doubleValue = number.doubleValue();
            if ((double) Math.round(doubleValue) == doubleValue)
                return (long) doubleValue;
            else
                return doubleValue;
        }
    }

    static {
        factory.start();
        for (Entry<Class<?>, SMethodHandle> entry : JavaArgumentConverters.CONVERTERS.entrySet()) {
            factory.add(new TypeConvertRule(entry.getValue(), entry.getKey(), Scriptable.class));
        }

        factory.add(new TypeConvertRule(MHHS.create1r(ListAdapter::create), List.class, NativeArray.class));
        factory.add(new TypeConvertRule(MHHS.create1r(ListAdapter::create), Deque.class, NativeArray.class));
        factory.add(new TypeConvertRule(MHHS.create1r(ListAdapter::create), Queue.class, NativeArray.class));
        factory.add(new TypeConvertRule(MHHS.create1r(ListAdapter::create), Collection.class, NativeArray.class));
        factory.add(new TypeConvertRule(MHHS.create1r(TypeConvertRules::createMirror), ScriptableMirror.class, Scriptable.class));
        factory.add(new TypeConvertRule(MHHS.create1r(TypeConvertRules::createMirror), AbstractJSObject.class, Scriptable.class));
        factory.add(new TypeConvertRule(MHHS.create1r(TypeConvertRules::createMirror), JSObject.class, Scriptable.class));
        factory.add(new TypeConvertRule(MHHS.create1r(TypeConvertRules::createMirror), Map.class, Scriptable.class));
        factory.add(new TypeConvertRule(MHHS.create1r(TypeConvertRules::createMirror), Bindings.class, Scriptable.class));

    }

    public static ScriptableMirror createMirror(Scriptable obj) {
        return obj != null ? ScriptableMirror.create(obj, Context.getCurrentContext()) : null;
    }

    static {
        factory.start();

        for (Entry<Class<?>, SMethodHandle> entry : JavaArgumentConverters.CONVERTERS.entrySet()) {
            factory.add(new TypeConvertRule(entry.getValue(), entry.getKey(), Number.class, CharSequence.class, Boolean.class));
        }

        SimpleJavaTypeConverter.addToFactory(factory::addDirect);
    }

    static {
        factory.start();
        factory.add(new TypeConvertRule(MHHS.create1r(ScriptRuntime::toBoolean), Boolean.TYPE));
        factory.add(new TypeConvertRule(MHHS.create1r(ScriptRuntime::toNumber), Double.TYPE));
        factory.add(new TypeConvertRule(MHHS.create1r(ScriptRuntime::toInt32), Integer.TYPE));
        factory.add(new TypeConvertRule(MHHS.create1r(TypeConvertRules::toLong), Long.TYPE));
        factory.add(new TypeConvertRule(MHHS.create1r(ScriptRuntime::toString), String.class));
    }


    private static long toLong(Object obj) {
        return (long)ScriptRuntime.toNumber(obj.toString());
    }

    private TypeConvertRules() {
    }

    private static class TypeConverterFactory {
        Map<Class<?>, List<TypeConvertRule>> ruleMap;
        Map<Entry<Class<?>, Class<?>>, List<TypeConvertRule>> primitiveSourceRuleMap;
        private Set<Class<?>> currentRules;

        private TypeConverterFactory() {
            this.ruleMap = new HashMap<>();
            this.primitiveSourceRuleMap = new HashMap<>();
        }

        public void add(TypeConvertRule rule) {
            if (this.currentRules.contains(rule.targetType)) {
                throw new IllegalArgumentException("duplicate target type: " + rule.targetType);
            } else {
                this.currentRules.add(rule.targetType);
                this.addDirect(rule);
            }
        }

        public void addDirect(TypeConvertRule rule) {
            for (Class<?> sourceType : rule.sourceTypes) {
                if (sourceType.isPrimitive()) {
                    List<TypeConvertRule> rules = this.primitiveSourceRuleMap
                            .computeIfAbsent(new SimpleImmutableEntry<>(sourceType, rule.targetType),
                                    (k) -> new ArrayList<>());
                    rules.add(rule);
                }
            }

            List<TypeConvertRule> rules = this.ruleMap.computeIfAbsent(rule.targetType, (k) -> new ArrayList<>());
            rules.add(rule);
        }

        public void start() {
            this.currentRules = new HashSet<>();
        }
    }
}
