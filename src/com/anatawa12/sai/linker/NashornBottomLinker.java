package com.anatawa12.sai.linker;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;

import com.anatawa12.sai.ScriptRuntime;

final class NashornBottomLinker implements GuardingTypeConverterFactory {
    @Override
    public GuardedTypeConversion convertToType(final Class<?> sourceType, final Class<?> targetType) {
        final MethodHandle mh = CONVERTERS.get(targetType);
        return mh == null ? null
                : new GuardedTypeConversion(new GuardedInvocation(mh)
                        .asType(MethodType.methodType(targetType, sourceType)), true);
    }

    private static final Map<Class<?>, MethodHandle> CONVERTERS = new HashMap<>();
    static {
        CONVERTERS.put(boolean.class, MHH.create1r(ScriptRuntime::toBoolean));
        CONVERTERS.put(double.class, MHH.create1r(ScriptRuntime::toNumber));
        CONVERTERS.put(int.class, MHH.create1r(ScriptRuntime::toInt32));
        CONVERTERS.put(long.class, MHH.create1r(NashornBottomLinker::toLong));
        CONVERTERS.put(String.class, MHH.create1r(ScriptRuntime::toString));
    }

    private static long toLong(Object obj) {
        return (long)ScriptRuntime.toNumber(obj.toString());
    }
}
