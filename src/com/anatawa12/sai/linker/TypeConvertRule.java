package com.anatawa12.sai.linker;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

public class TypeConvertRule {
    public final SMethodHandle converter;
    public final Class<?> targetType;
    public final Class<?>[] sourceTypes;
    public final boolean anyObjectSource;
    public final boolean anyPrimitive;
    public final MethodHandle dynamicGuard = MHH.create1r(this::dynamicGuard);

    public TypeConvertRule(SMethodHandle converter, Class<?> targetType, Class<?>... sourceTypes) {
        MethodType type = converter.type();

        if (sourceTypes.length == 0) {
            if (type.parameterCount() != 1)
                throw new IllegalArgumentException("param count of converter for value must be 0");

            if (type.parameterType(0) != Object.class)
                throw new IllegalArgumentException("param type of converter for any value must be Object");
        } else {
            if (type.parameterCount() != 1)
                throw new IllegalArgumentException("param count of converter for value must be 1");

            Class<?> paramType = type.parameterType(0);

            for (Class<?> sourceType : sourceTypes) {
                if (!TypeUtil.isConvertibleWithoutLoss(sourceType, paramType))
                    throw new IllegalArgumentException("cannot call converter with some specified source type.");
            }
        }

        if (type.returnType() == Void.TYPE) throw new IllegalArgumentException("converter cannot return void");
        if (!TypeUtil.isConvertibleWithoutLoss(type.returnType(), targetType))
            throw new IllegalArgumentException("return type of converter cannot be converted to targetType");
        boolean anyObjectSource = false;
        boolean anyPrimitive = false;

        for (Class<?> sourceType : sourceTypes) {
            if (sourceType.isInterface()) {
                anyObjectSource = true;
            }

            if (sourceType.isPrimitive()) {
                anyPrimitive = true;
            }
        }

        this.converter = converter;
        this.targetType = targetType;
        this.sourceTypes = sourceTypes;
        this.anyObjectSource = anyObjectSource;
        this.anyPrimitive = anyPrimitive;
    }

    public boolean staticGuard(Class<?> sourceType) {
        for (Class<?> type : sourceTypes) {
            if (type.isAssignableFrom(sourceType)) {
                return true;
            }
        }

        return false;
    }

    public boolean canUseThisRule(Class<?> sourceType) {
        if (this.anyObjectSource) {
            if (!sourceType.isPrimitive()) {
                return true;
            }

            if (!this.anyPrimitive) {
                return false;
            }
        }

        for (Class<?> type : sourceTypes) {
            if (type.isAssignableFrom(sourceType)) {
                return true;
            }

            if (sourceType.isAssignableFrom(type)) {
                return true;
            }
        }

        return false;
    }

    private boolean dynamicGuard(Object value) {
        for (Class<?> type : sourceTypes) {
            if (type.isInstance(value)) {
                return true;
            }
        }

        return false;
    }
}
