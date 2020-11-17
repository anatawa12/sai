package com.anatawa12.sai.linker;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;

import com.anatawa12.sai.RuntimeErrors;
import com.anatawa12.sai.ScriptRuntime;
import com.anatawa12.sai.Scriptable;
import com.anatawa12.sai.Undefined;

final class JavaArgumentConverters {
    private static final MethodHandle TO_DOUBLE         = MHH.create1r(JavaArgumentConverters::toDouble);
    private static final MethodHandle TO_LONG           = MHH.create1r(JavaArgumentConverters::toLong);
    private static final MethodHandle TO_LONG_PRIMITIVE = MHH.create1r(JavaArgumentConverters::toLongPrimitive);

    private JavaArgumentConverters() {
    }

    static MethodHandle getConverter(final Class<?> targetType) {
        return CONVERTERS.get(targetType);
    }

    private static Boolean toBoolean(final Object obj) {
        if (obj == null) {
            return null;
        }

        return ScriptRuntime.toBoolean(obj);
    }

    private static Character toChar(final Object o) {
        if (o == null) {
            return null;
        }

        if (o instanceof Number) {
            final int intValue = ((Number)o).intValue();
            if (intValue >= Character.MIN_VALUE && intValue <= Character.MAX_VALUE) {
                return (char) intValue;
            }

            throw RuntimeErrors.reportRuntimeError0("msg.conversion.not.allowed.number.to.char");
        }

        final String s = toString(o);
        if (s == null) {
            return null;
        }

        if (s.length() != 1) {
            throw RuntimeErrors.reportRuntimeError0("msg.conversion.not.allowed.string.to.char");
        }

        return s.charAt(0);
    }

    static char toCharPrimitive(final Object obj0) {
        final Character c = toChar(obj0);
        return c == null ? (char)0 : c;
    }

    // returns null if the obj is null
    static String toString(final Object obj) {
        return obj == null ? null : ScriptRuntime.toString(obj);
    }

    private static Double toDouble(Object obj) {
        while (true) {
            if (obj == null) {
                return null;
            } else if (obj instanceof Double) {
                return (Double) obj;
            } else if (obj instanceof Number) {
                return ((Number)obj).doubleValue();
            } else if (obj instanceof CharSequence) {
                return ScriptRuntime.toNumber(obj.toString());
            } else if (obj instanceof Boolean) {
                return (Boolean) obj ? 1 : +0.0;
            } else if (obj instanceof Scriptable) {
                obj = ScriptRuntime.toPrimitive(obj, Number.class);
                continue;
            } else if (obj == Undefined.instance) {
                return Double.NaN;
            }
            throw assertUnexpectedType(obj);
        }
    }

    private static Number toNumber(Object obj) {
        while (true) {
            if (obj == null) {
                return null;
            } else if (obj instanceof Number) {
                return (Number) obj;
            } else if (obj instanceof CharSequence) {
                return ScriptRuntime.toNumber((String) obj);
            } else if (obj instanceof Boolean) {
                return (Boolean) obj ? 1 : +0.0;
            } else if (obj instanceof Scriptable) {
                obj = ScriptRuntime.toPrimitive(obj, Number.class);
                continue;
            } else if (obj == Undefined.instance) {
                return Double.NaN;
            }
            throw assertUnexpectedType(obj);
        }
    }

    private static Long toLong(Object obj) {
        while (true) {
            if (obj == null) {
                return null;
            } else if (obj instanceof Long) {
                return (Long) obj;
            } else if (obj instanceof Integer) {
                return ((Integer)obj).longValue();
            } else if (obj instanceof Double) {
                final Double d = (Double)obj;
                if(Double.isInfinite(d)) {
                    return 0L;
                }
                return d.longValue();
            } else if (obj instanceof Float) {
                final Float f = (Float)obj;
                if(Float.isInfinite(f)) {
                    return 0L;
                }
                return f.longValue();
            } else if (obj instanceof Number) {
                return ((Number)obj).longValue();
            } else if (obj instanceof CharSequence) {
                return (long)ScriptRuntime.toNumber(obj.toString());
            } else if (obj instanceof Boolean) {
                return (Boolean)obj ? 1L : 0L;
            } else if (obj instanceof Scriptable) {
                obj = ScriptRuntime.toPrimitive(obj, Number.class);
                continue;
            } else if (obj == Undefined.instance) {
                return null;
            }
            throw assertUnexpectedType(obj);
        }
    }

    private static AssertionError assertUnexpectedType(final Object obj) {
        return new AssertionError("Unexpected type" + obj.getClass().getName() + ". Guards should have prevented this");
    }

    private static long toLongPrimitive(final Object obj0) {
        final Long l = toLong(obj0);
        return l == null ? 0L : l;
    }

    private static final Map<Class<?>, MethodHandle> CONVERTERS = new HashMap<>();

    static {
        CONVERTERS.put(Number.class, MHH.create1r(JavaArgumentConverters::toNumber));
        CONVERTERS.put(String.class, MHH.create1r(JavaArgumentConverters::toString));

        CONVERTERS.put(boolean.class, MHH.create1r(ScriptRuntime::toBoolean));
        CONVERTERS.put(Boolean.class, MHH.create1r(JavaArgumentConverters::toBoolean));

        CONVERTERS.put(char.class, MHH.create1r(JavaArgumentConverters::toCharPrimitive));
        CONVERTERS.put(Character.class, MHH.create1r(JavaArgumentConverters::toChar));

        CONVERTERS.put(double.class, MHH.create1r(ScriptRuntime::toNumber));
        CONVERTERS.put(Double.class, TO_DOUBLE);

        CONVERTERS.put(long.class, TO_LONG_PRIMITIVE);
        CONVERTERS.put(Long.class, TO_LONG);

        putLongConverter(Byte.class, MHH.create1r(JavaArgumentConverters::byteValue));
        putLongConverter(Short.class, MHH.create1r(JavaArgumentConverters::shortValue));
        putLongConverter(Integer.class, MHH.create1r(JavaArgumentConverters::intValue));
        putDoubleConverter(Float.class, MHH.create1r(JavaArgumentConverters::floatValue));
    }

    private static void putLongConverter(final Class<?> targetType, MethodHandle handle) {
        final Class<?> primitive = TypeUtil.getPrimitiveType(targetType);
        CONVERTERS.put(primitive,  MethodHandles.explicitCastArguments(TO_LONG_PRIMITIVE, TO_LONG_PRIMITIVE.type().changeReturnType(primitive)));
        CONVERTERS.put(targetType, MethodHandles.filterReturnValue(TO_LONG, handle));
    }

    private static void putDoubleConverter(final Class<?> targetType, MethodHandle handle) {
        final Class<?> primitive = TypeUtil.getPrimitiveType(targetType);
        CONVERTERS.put(primitive,  MethodHandles.explicitCastArguments(MHH.create1r(ScriptRuntime::toNumber), MethodType.methodType(primitive, Object.class)));
        CONVERTERS.put(targetType, MethodHandles.filterReturnValue(TO_DOUBLE, handle));
    }

    private static Byte byteValue(final Long l) {
        return l == null ? null : l.byteValue();
    }

    private static Short shortValue(final Long l) {
        return l == null ? null : l.shortValue();
    }

    private static Integer intValue(final Long l) {
        return l == null ? null : l.intValue();
    }

    private static Float floatValue(final Double d) {
        return d == null ? null : d.floatValue();
    }
}
