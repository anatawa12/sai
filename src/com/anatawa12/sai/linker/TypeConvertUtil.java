package com.anatawa12.sai.linker;

public class TypeConvertUtil {
    private static final ClassValue<ClassValue<Boolean>> canConvert = new ClassValue<ClassValue<Boolean>>() {
        protected ClassValue<Boolean> computeValue(final Class<?> sourceType) {
            return new ClassValue<Boolean>() {
                protected Boolean computeValue(Class<?> targetType) {
                    try {
                        return TypeConvertRules.getConverterOrNull(sourceType, targetType) != null;
                    } catch (RuntimeException var3) {
                        throw var3;
                    } catch (Exception var4) {
                        throw new RuntimeException(var4);
                    }
                }
            };
        }
    };

    public TypeConvertUtil() {
    }

    public static boolean canConvert(Class<?> from, Class<?> to) {
        return canAutoConvert(from, to) || canConvert.get(from).get(to);
    }

    private static boolean canAutoConvert(Class<?> fromType, Class<?> toType) {
        return TypeUtil.isMethodInvocationConvertible(fromType, toType);
    }
}
