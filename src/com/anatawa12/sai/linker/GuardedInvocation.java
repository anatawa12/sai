package com.anatawa12.sai.linker;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.util.Objects;

public class GuardedInvocation {
    private final MethodHandle invocation;
    private final MethodHandle guard;

    public GuardedInvocation(final MethodHandle invocation) {
        this(invocation, null);
    }

    public GuardedInvocation(final MethodHandle invocation, final MethodHandle guard) {
        this.invocation = Objects.requireNonNull(invocation);
        this.guard = guard;
    }

    public void assertType(final MethodType type) {
        assertType(invocation, type);
        if (guard != null) {
            assertType(guard, type.changeReturnType(Boolean.TYPE));
        }
    }

    public GuardedInvocation asType(final MethodType newType) {
        MethodHandle castedInvocation = invocation.asType(newType);
        MethodHandle castedGuard = guard == null ? null : GuardHelper.asType(guard, newType);

        if (castedInvocation == invocation && castedGuard == guard) {
            return this;
        }
        return new GuardedInvocation(castedInvocation, castedGuard);
    }

    public MethodHandle compose(final MethodHandle fallback) {
        return guard == null
                ? invocation
                : MethodHandles.guardWithTest(guard, invocation, fallback);
    }

    private static void assertType(final MethodHandle mh, final MethodType type) {
        if(!mh.type().equals(type)) {
            throw new WrongMethodTypeException("Expected type: " + type + " actual type: " + mh.type());
        }
    }

    private static class GuardHelper {
        public static MethodHandle asType(MethodHandle test, MethodType type) {
            return test.asType(getTestType(test, type));
        }

        private static MethodType getTestType(MethodHandle test, MethodType type) {
            return type.dropParameterTypes(test.type().parameterCount(), type.parameterCount()).changeReturnType(Boolean.TYPE);
        }
    }
}
