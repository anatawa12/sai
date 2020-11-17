package com.anatawa12.sai.linker;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class MethodOrConstructor {
    private final Executable executable;
    private Class<?>[] params;

    public MethodOrConstructor(Executable executable) {
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

    public boolean isStatic() {
        return Modifier.isStatic(executable.getModifiers());
    }

    public Class<?> getDeclaringClass() {
        return executable.getDeclaringClass();
    }

    public boolean isMethod() {
        return executable instanceof Method;
    }

    public boolean isConstructor() {
        return executable instanceof Constructor<?>;
    }

    public String name() {
        return executable.getName();
    }

    public Method asMethod() {
        return (Method)executable;
    }

    public Constructor<?> asConstructor() {
        return (Constructor<?>)executable;
    }
}
