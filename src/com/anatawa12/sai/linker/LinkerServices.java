package com.anatawa12.sai.linker;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.List;

public class LinkerServices {
    public static final LinkerServices INSTANCE;

    static {
        final List<GuardingTypeConverterFactory> linkers = Arrays.asList(
                new RhinoExtraLinker(),
                new NashornLinker(),
                new NashornPrimitiveLinker(),
                new NashornBottomLinker());

        INSTANCE = new LinkerServices(new TypeConverterFactory(linkers));
    }

    private final TypeConverterFactory typeConverterFactory;

    private LinkerServices(final TypeConverterFactory typeConverterFactory) {
        this.typeConverterFactory = typeConverterFactory;
    }

    public boolean canConvert(final Class<?> from, final Class<?> to) {
        return typeConverterFactory.canConvert(from, to);
    }

    public MethodHandle asType(final MethodHandle handle, final MethodType fromType) {
        return typeConverterFactory.asType(handle, fromType);
    }

    public MethodHandle asTypeLosslessReturn(final MethodHandle handle, final MethodType fromType) {
        final Class<?> handleReturnType = handle.type().returnType();
        return asType(handle, TypeUtil.isConvertibleWithoutLoss(handleReturnType, fromType.returnType()) ?
                fromType : fromType.changeReturnType(handleReturnType));
    }

    public MethodHandle getTypeConverter(final Class<?> sourceType, final Class<?> targetType) {
        return typeConverterFactory.getTypeConverter(sourceType, targetType);
    }

    public ConversionComparison compareConversion(final Class<?> sourceType, final Class<?> targetType1, final Class<?> targetType2) {
        return typeConverterFactory.compareConversion(sourceType, targetType1, targetType2);
    }
}
