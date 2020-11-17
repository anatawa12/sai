package com.anatawa12.sai.linker;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import com.anatawa12.sai.Undefined;

final class NashornPrimitiveLinker implements GuardingTypeConverterFactory, ConversionComparator {
    private static final GuardedTypeConversion VOID_TO_OBJECT = new GuardedTypeConversion(
            new GuardedInvocation(MethodHandles.constant(Object.class, Undefined.instance)), true);

    private static boolean canLinkTypeStatic(final Class<?> type) {
        return type != Undefined.class && com.anatawa12.sai.ScriptRuntime.isPrimitiveType(type);
    }

    @Override
    public GuardedTypeConversion convertToType(final Class<?> sourceType, final Class<?> targetType) {
        final MethodHandle mh = JavaArgumentConverters.getConverter(targetType);
        if (mh == null) {
            if(targetType == Object.class && sourceType == void.class) {
                return VOID_TO_OBJECT;
            }
            return null;
        }

        return new GuardedTypeConversion(new GuardedInvocation(mh, canLinkTypeStatic(sourceType) ? null : GUARD_PRIMITIVE).asType(mh.type().changeParameterType(0, sourceType)), true);
    }

    @Override
    public ConversionComparison compareConversion(final Class<?> sourceType, final Class<?> targetType1, final Class<?> targetType2) {
        // if the type is same as wrapped type, that's good one
        final Class<?> wrapper1 = getWrapperTypeOrSelf(targetType1);
        if (sourceType == wrapper1) {
            return ConversionComparison.TYPE_1_BETTER;
        }
        final Class<?> wrapper2 = getWrapperTypeOrSelf(targetType2);
        if (sourceType == wrapper2) {
            return ConversionComparison.TYPE_2_BETTER;
        }

        if (Number.class.isAssignableFrom(sourceType)) {
            // If exactly one of the targets is a number, pick it.
            if (Number.class.isAssignableFrom(wrapper1)) {
                if (!Number.class.isAssignableFrom(wrapper2)) {
                    return ConversionComparison.TYPE_1_BETTER;
                }
            } else {
                if (Number.class.isAssignableFrom(wrapper2)) {
                    return ConversionComparison.TYPE_2_BETTER;
                }
            }

            // If exactly one of the targets is a character, pick it. Numbers can be reasonably converted to chars using
            // the UTF-16 values.
            if (Character.class == wrapper1) {
                return ConversionComparison.TYPE_1_BETTER;
            } else if (Character.class == wrapper2) {
                return ConversionComparison.TYPE_2_BETTER;
            }
        }

        if (sourceType == String.class || sourceType == Boolean.class || Number.class.isAssignableFrom(sourceType)) {
            final Class<?> primitiveType1 = getPrimitiveTypeOrSelf(targetType1);
            final Class<?> primitiveType2 = getPrimitiveTypeOrSelf(targetType2);
            // choose wider one
            if (TypeUtil.isMethodInvocationConvertible(primitiveType1, primitiveType2)) {
                return ConversionComparison.TYPE_2_BETTER;
            } else if (TypeUtil.isMethodInvocationConvertible(primitiveType2, primitiveType1)) {
                return ConversionComparison.TYPE_1_BETTER;
            }
            if (targetType1 == String.class) {
                return ConversionComparison.TYPE_1_BETTER;
            }
            if (targetType2 == String.class) {
                return ConversionComparison.TYPE_2_BETTER;
            }
        }

        return ConversionComparison.INDETERMINATE;
    }

    private static Class<?> getPrimitiveTypeOrSelf(final Class<?> type) {
        final Class<?> primitive = TypeUtil.getPrimitiveType(type);
        return primitive == null ? type : primitive;
    }

    private static Class<?> getWrapperTypeOrSelf(final Class<?> type) {
        final Class<?> wrapper = TypeUtil.getWrapperType(type);
        return wrapper == null ? type : wrapper;
    }

    private static boolean isJavaScriptPrimitive(final Object o) {
        return o instanceof CharSequence || o instanceof Boolean || o instanceof Number || o == null;
    }

    private static final MethodHandle GUARD_PRIMITIVE = MHH.create1r(NashornPrimitiveLinker::isJavaScriptPrimitive);
}
