package com.anatawa12.sai.linker;

import com.anatawa12.sai.ScriptRuntime;
import java.util.function.Consumer;

public class SimpleJavaTypeConverter {
    public SimpleJavaTypeConverter() {
    }

    public static void addToFactory(Consumer<TypeConvertRule> addDirect) {
        // to String
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::byteToString), String.class, byte.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::shortToString), String.class, short.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::intToString), String.class, int.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::longToString), String.class, long.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::floatToString), String.class, float.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::doubleToString), String.class, double.class));

        // to char
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::byteToChar), char.class, byte.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::shortToChar), char.class, short.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::intToChar), char.class, int.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::longToChar), char.class, long.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::floatToChar), char.class, float.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::doubleToChar), char.class, double.class));

        // to char object
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::byteToCharObject), Character.class, byte.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::shortToCharObject), Character.class, short.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::intToCharObject), Character.class, int.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::longToCharObject), Character.class, long.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::floatToCharObject), Character.class, float.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::doubleToCharObject), Character.class, double.class));

        // to primitives
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::shortToByte), byte.class, short.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::intToByte), byte.class, int.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::intToShort), short.class, int.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::longToByte), byte.class, long.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::longToShort), short.class, long.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::longToInt), int.class, long.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::floatToByte), byte.class, float.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::floatToInt), int.class, float.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::floatToShort), short.class, float.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::floatToLong), long.class, float.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::doubleToByte), byte.class, double.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::doubleToInt), int.class, double.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::doubleToShort), short.class, double.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::doubleToLong), long.class, double.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::doubleToFloat), float.class, double.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::shortToByteObject), Byte.class, short.class));

        // to primitive objects
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::intToByteObject), Byte.class, int.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::intToShortObject), Short.class, int.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::longToByteObject), Byte.class, long.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::longToShortObject), Short.class, long.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::longToIntObject), Integer.class, long.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::floatToByteObject), Byte.class, float.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::floatToIntObject), Integer.class, float.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::floatToShortObject), Short.class, float.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::floatToLongObject), Long.class, float.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::doubleToByteObject), Byte.class, double.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::doubleToIntObject), Integer.class, double.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::doubleToShortObject), Short.class, double.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::doubleToLongObject), Long.class, double.class));
        addDirect.accept(new TypeConvertRule(MHHS.create1r(SimpleJavaTypeConverter::doubleToFloatObject), Float.class, double.class));
    }

    public static String byteToString(byte value) {
        return ScriptRuntime.toString((double)value);
    }

    public static String shortToString(short value) {
        return ScriptRuntime.toString((double)value);
    }

    public static String intToString(int value) {
        return ScriptRuntime.toString((double)value);
    }

    public static String longToString(long value) {
        return ScriptRuntime.toString((double)value);
    }

    public static String floatToString(float value) {
        return ScriptRuntime.toString((double)value);
    }

    public static String doubleToString(double value) {
        return ScriptRuntime.toString(value);
    }

    public static char byteToChar(byte value) {
        return JavaArgumentConverters.intToCharPrimitive(value);
    }

    public static char shortToChar(short value) {
        return JavaArgumentConverters.intToCharPrimitive(value);
    }

    public static char intToChar(int value) {
        return JavaArgumentConverters.intToCharPrimitive(value);
    }

    public static char longToChar(long value) {
        return JavaArgumentConverters.intToCharPrimitive((int)value);
    }

    public static char floatToChar(float value) {
        return JavaArgumentConverters.intToCharPrimitive((int)value);
    }

    public static char doubleToChar(double value) {
        return JavaArgumentConverters.intToCharPrimitive((int)value);
    }

    public static Character byteToCharObject(byte value) {
        return JavaArgumentConverters.intToCharPrimitive(value);
    }

    public static Character shortToCharObject(short value) {
        return JavaArgumentConverters.intToCharPrimitive(value);
    }

    public static Character intToCharObject(int value) {
        return JavaArgumentConverters.intToCharPrimitive(value);
    }

    public static Character longToCharObject(long value) {
        return JavaArgumentConverters.intToCharPrimitive((int)value);
    }

    public static Character floatToCharObject(float value) {
        return JavaArgumentConverters.intToCharPrimitive((int)value);
    }

    public static Character doubleToCharObject(double value) {
        return JavaArgumentConverters.intToCharPrimitive((int)value);
    }

    public static byte shortToByte(short value) {
        return (byte)value;
    }

    public static byte intToByte(int value) {
        return (byte)value;
    }

    public static short intToShort(int value) {
        return (short)value;
    }

    public static byte longToByte(long value) {
        return (byte)((int)value);
    }

    public static short longToShort(long value) {
        return (short)((int)value);
    }

    public static int longToInt(long value) {
        return (int)value;
    }

    public static byte floatToByte(float value) {
        return (byte)((int)value);
    }

    public static int floatToInt(float value) {
        return (int)value;
    }

    public static short floatToShort(float value) {
        return (short)((int)value);
    }

    public static long floatToLong(float value) {
        return (long)value;
    }

    public static byte doubleToByte(double value) {
        return (byte)((int)value);
    }

    public static int doubleToInt(double value) {
        return (int)value;
    }

    public static short doubleToShort(double value) {
        return (short)((int)value);
    }

    public static long doubleToLong(double value) {
        return (long)value;
    }

    public static float doubleToFloat(double value) {
        return (float)value;
    }

    public static Byte shortToByteObject(short value) {
        return (byte)value;
    }

    public static Byte intToByteObject(int value) {
        return (byte)value;
    }

    public static Short intToShortObject(int value) {
        return (short)value;
    }

    public static Byte longToByteObject(long value) {
        return (byte)((int)value);
    }

    public static Short longToShortObject(long value) {
        return (short)((int)value);
    }

    public static Integer longToIntObject(long value) {
        return (int)value;
    }

    public static Byte floatToByteObject(float value) {
        return (byte)((int)value);
    }

    public static Integer floatToIntObject(float value) {
        return (int)value;
    }

    public static Short floatToShortObject(float value) {
        return (short)((int)value);
    }

    public static Long floatToLongObject(float value) {
        return (long)value;
    }

    public static Byte doubleToByteObject(double value) {
        return (byte)((int)value);
    }

    public static Integer doubleToIntObject(double value) {
        return (int)value;
    }

    public static Short doubleToShortObject(double value) {
        return (short)((int)value);
    }

    public static Long doubleToLongObject(double value) {
        return (long)value;
    }

    public static Float doubleToFloatObject(double value) {
        return (float)value;
    }
}
