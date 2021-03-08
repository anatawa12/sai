package com.anatawa12.sai.linker;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Member;

public final class SMethodHandle {
    private final MethodHandle real;

    private SMethodHandle(MethodHandle real) {
        this.real = real;
    }

    public static SMethodHandle direct(MethodHandle real) {
        MethodHandles.reflectAs(Member.class, real);
        return new SMethodHandle(real);
    }

    public MethodHandle getReal() {
        return real;
    }

    public MethodType type() {
        return this.getReal().type();
    }
}
