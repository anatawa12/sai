package com.anatawa12.sai.linker;

import com.anatawa12.sai.Context;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

final class RhinoExtraLinker implements GuardingTypeConverterFactory, ConversionComparator {
    @Override
    public GuardedTypeConversion convertToType(final Class<?> sourceType, final Class<?> targetType) {
        MethodType methodType = MethodType.methodType(targetType, sourceType);

        // if target is object and source is Number
        if (targetType == Object.class && Number.class.isAssignableFrom(sourceType)) {
            Context ctx = Context.getCurrentContext();
            if (ctx != null && ctx.hasFeature(Context.FEATURE_INTEGER_WITHOUT_DECIMAL_PLACE)) {
                return new GuardedTypeConversion(
                        new GuardedInvocation(NUMBER_TO_LONG_OR_DOUBLE.asType(methodType), null), true);
            }
        }

        return null;
    }

    private static final MethodHandle NUMBER_TO_LONG_OR_DOUBLE = MHH.create1r(RhinoExtraLinker::numberToLongOrDouble);

    public static Object numberToLongOrDouble(Number arg) {
        if (arg == null) return null;
        double doubleValue = arg.doubleValue();
        if (Math.round(doubleValue) == doubleValue) {
            return (long)doubleValue;
        }
        return doubleValue;
    }

    @Override
    public ConversionComparison compareConversion(final Class<?> sourceType, final Class<?> targetType1, final Class<?> targetType2) {
        return ConversionComparison.INDETERMINATE;
    }
}

