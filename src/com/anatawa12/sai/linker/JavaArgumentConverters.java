package com.anatawa12.sai.linker;

import com.anatawa12.sai.RuntimeErrors;
import com.anatawa12.sai.ScriptRuntime;
import com.anatawa12.sai.Scriptable;
import com.anatawa12.sai.Undefined;
import java.util.HashMap;
import java.util.Map;

final class JavaArgumentConverters {
    public static final Map<Class<?>, SMethodHandle> CONVERTERS = new HashMap<>();

    private JavaArgumentConverters() {
    }

    public static Boolean toBoolean(Object obj) {
        return obj == null ? null : ScriptRuntime.toBoolean(obj);
    }

    public static char intToCharPrimitive(int intValue) {
        if (intValue >= 0 && intValue <= 65535) {
            return (char) intValue;
        } else {
            throw RuntimeErrors.reportRuntimeError0("msg.conversion.not.allowed.number.to.char");
        }
    }

    public static Character toChar(Object o) {
        if (o == null) return null;

        if (o instanceof Number) {
            return intToCharPrimitive(((Number) o).intValue());
        }

        String s = toString(o);
        if (s == null) return null;

        if (s.length() != 1) throw RuntimeErrors.reportRuntimeError0("msg.conversion.not.allowed.string.to.char");
        return s.charAt(0);
    }

    public static char toCharPrimitive(Object obj0) {
        Character c = toChar(obj0);
        return c == null ? 0 : c;
    }

    public static String toString(Object obj) {
        return obj == null ? null : ScriptRuntime.toString(obj);
    }

    public static Double toDouble(Object obj) {
        while (true) {
            if (obj == null) return null;

            if (obj instanceof Double)
                return (Double) obj;

            if (obj instanceof Number)
                return ((Number) obj).doubleValue();

            if (obj instanceof CharSequence)
                return ScriptRuntime.toNumber(obj.toString());

            if (obj instanceof Boolean)
                return (Boolean) obj ? 1.0D : 0.0D;

            if (obj instanceof Scriptable) {
                obj = ScriptRuntime.toPrimitive(obj, Number.class);
                continue;
            }

            if (obj == Undefined.instance)
                return Double.NaN;

            throw assertUnexpectedType(obj);
        }
    }

    public static Number toNumber(Object obj) {
        while (true) {
            if (obj == null) return null;

            if (obj instanceof Number)
                return (Number) obj;

            if (obj instanceof CharSequence)
                return ScriptRuntime.toNumber((String) obj);

            if (obj instanceof Boolean)
                return (boolean) obj ? 1.0D : 0.0D;

            if (obj instanceof Scriptable) {
                obj = ScriptRuntime.toPrimitive(obj, Number.class);
                continue;
            }

            if (obj == Undefined.instance)
                return Double.NaN;

            throw assertUnexpectedType(obj);
        }
    }

    public static Long toLong(Object obj) {
        while (true) {
            if (obj == null)
                return null;

            if (obj instanceof Long)
                return (Long) obj;

            if (obj instanceof Integer)
                return ((Integer) obj).longValue();

            if (obj instanceof Double) {
                Double d = (Double) obj;
                if (Double.isInfinite(d)) {
                    return 0L;
                }

                return d.longValue();
            }

            if (obj instanceof Float) {
                Float f = (Float) obj;
                if (Float.isInfinite(f)) {
                    return 0L;
                }

                return f.longValue();
            }

            if (obj instanceof Number)
                return ((Number) obj).longValue();

            if (obj instanceof CharSequence)
                return (long) ScriptRuntime.toNumber(obj.toString());

            if (obj instanceof Boolean)
                return (Boolean) obj ? 1L : 0L;

            if (obj instanceof Scriptable) {
                obj = ScriptRuntime.toPrimitive(obj, Number.class);
                continue;
            }

            if (obj == Undefined.instance)
                return null;

            throw assertUnexpectedType(obj);
        }
    }

    private static AssertionError assertUnexpectedType(Object obj) {
        return new AssertionError("Unexpected type" + obj.getClass().getName());
    }

    public static long toLongPrimitive(Object obj0) {
        Long l = toLong(obj0);
        return l == null ? 0L : l;
    }

    public static byte toBytePrimitive(Object obj) {
        return (byte) toLongPrimitive(obj);
    }

    public static Byte toByte(Object obj) {
        Long value = toLong(obj);
        return value == null ? null : value.byteValue();
    }

    public static short toShortPrimitive(Object obj) {
        return (short) toLongPrimitive(obj);
    }

    public static Short toShort(Object obj) {
        Long value = toLong(obj);
        return value == null ? null : value.shortValue();
    }

    public static int toIntPrimitive(Object obj) {
        return (int) toLongPrimitive(obj);
    }

    public static Integer toInt(Object obj) {
        Long value = toLong(obj);
        return value == null ? null : value.intValue();
    }

    public static float toFloatPrimitive(Object obj) {
        return (float) ScriptRuntime.toNumber(obj);
    }

    public static Float toFloat(Object obj) {
        Double value = toDouble(obj);
        return value == null ? null : value.floatValue();
    }

    static {
        CONVERTERS.put(Number.class, MHHS.create1r(JavaArgumentConverters::toNumber));
        CONVERTERS.put(String.class, MHHS.create1r(JavaArgumentConverters::toString));
        CONVERTERS.put(Boolean.TYPE, MHHS.create1r(ScriptRuntime::toBoolean));
        CONVERTERS.put(Boolean.class, MHHS.create1r(JavaArgumentConverters::toBoolean));
        CONVERTERS.put(Character.TYPE, MHHS.create1r(JavaArgumentConverters::toCharPrimitive));
        CONVERTERS.put(Character.class, MHHS.create1r(JavaArgumentConverters::toChar));
        CONVERTERS.put(Double.TYPE, MHHS.create1r(ScriptRuntime::toNumber));
        CONVERTERS.put(Double.class, MHHS.create1r(JavaArgumentConverters::toDouble));
        CONVERTERS.put(Long.TYPE, MHHS.create1r(JavaArgumentConverters::toLongPrimitive));
        CONVERTERS.put(Long.class, MHHS.create1r(JavaArgumentConverters::toLong));
        CONVERTERS.put(Byte.TYPE, MHHS.create1r(JavaArgumentConverters::toBytePrimitive));
        CONVERTERS.put(Byte.class, MHHS.create1r(JavaArgumentConverters::toByte));
        CONVERTERS.put(Short.TYPE, MHHS.create1r(JavaArgumentConverters::toShortPrimitive));
        CONVERTERS.put(Short.class, MHHS.create1r(JavaArgumentConverters::toShort));
        CONVERTERS.put(Integer.TYPE, MHHS.create1r(JavaArgumentConverters::toIntPrimitive));
        CONVERTERS.put(Integer.class, MHHS.create1r(JavaArgumentConverters::toInt));
        CONVERTERS.put(Float.TYPE, MHHS.create1r(JavaArgumentConverters::toFloatPrimitive));
        CONVERTERS.put(Float.class, MHHS.create1r(JavaArgumentConverters::toFloat));
    }
}
