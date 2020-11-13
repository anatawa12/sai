package com.anatawa12.sai.linker;

import com.anatawa12.sai.Context;
import com.anatawa12.sai.EvaluatorException;
import com.anatawa12.sai.NativeArray;
import com.anatawa12.sai.NativeJavaObject;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Array;

public class ConvertingHelper {
    public static final MethodHandle TO_JAVA_ARRAY = MHH.create2r(ConvertingHelper::toJavaArray);

    private static Object toJavaArray(NativeArray array, Class<?> componentType) {
        long length = array.getLength();
        Object result = Array.newInstance(componentType, (int)length);
        for (int i = 0 ; i < length ; ++i) {
            try  {
                Array.set(result, i, Context.jsToJava(array.get(i, array), componentType));
            }
            catch (EvaluatorException ee) {
                NativeJavaObject.reportConversionError(array, result.getClass());
            }
        }
        return result;
    }
}
