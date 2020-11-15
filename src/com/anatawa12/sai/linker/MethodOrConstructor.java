package com.anatawa12.sai.linker;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;

public class MethodOrConstructor {
    private final Executable executable;
    private Class<?>[] params;

    protected MethodOrConstructor(Executable executable) {
        this.executable = executable;
    }

    public boolean isVarArgs() {
        return executable.isVarArgs();
    }

    public int parameterCount() {
        return executable.getParameterCount();
    }

    public Class<?>[] parameterArray() {
        return params != null ? params : (params = executable.getParameterTypes());
    }

    public Class<?> parameterType(int i) {
        return parameterArray()[i];
    }

    public Class<?> returnType() {
        if (executable instanceof Constructor<?>)
            return executable.getDeclaringClass();
        return ((Method)executable).getReturnType();
    }
}
