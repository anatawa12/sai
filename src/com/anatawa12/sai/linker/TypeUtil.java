package com.anatawa12.sai.linker;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

public class TypeUtil {
    private static final Map<Class<?>, Class<?>> WRAPPER_TYPES;
    private static final Map<Class<?>, Class<?>> PRIMITIVE_TYPES;

    static {
        Map<Class<?>, Class<?>> types = new IdentityHashMap<>(8);
        types.put(Boolean.TYPE, Boolean.class);
        types.put(Byte.TYPE, Byte.class);
        types.put(Character.TYPE, Character.class);
        types.put(Short.TYPE, Short.class);
        types.put(Integer.TYPE, Integer.class);
        types.put(Long.TYPE, Long.class);
        types.put(Float.TYPE, Float.class);
        types.put(Double.TYPE, Double.class);
        WRAPPER_TYPES = Collections.unmodifiableMap(types);
        types = new IdentityHashMap<>(8);
        types.put(Boolean.class, Boolean.TYPE);
        types.put(Byte.class, Byte.TYPE);
        types.put(Character.class, Character.TYPE);
        types.put(Short.class, Short.TYPE);
        types.put(Integer.class, Integer.TYPE);
        types.put(Long.class, Long.TYPE);
        types.put(Float.class, Float.TYPE);
        types.put(Double.class, Double.TYPE);
        types.put(Void.class, Void.TYPE);
        PRIMITIVE_TYPES = Collections.unmodifiableMap(types);
    }

    public static boolean isConvertibleWithoutLoss(Class<?> sourceType, Class<?> targetType) {
        if (targetType.isAssignableFrom(sourceType) || targetType == Void.TYPE) return true;
        if (!sourceType.isPrimitive()) return false;

        if (sourceType == Void.TYPE) {
            return targetType == Object.class;
        } else if (targetType.isPrimitive()) {
            return isProperPrimitiveLosslessSubtype(sourceType, targetType);
        } else {
            return targetType.isAssignableFrom(WRAPPER_TYPES.get(sourceType));
        }
    }

    private static boolean isProperPrimitiveLosslessSubtype(Class<?> subType, Class<?> superType) {
        if (superType == Boolean.TYPE || subType == Boolean.TYPE) return false;
        if (superType == Character.TYPE || subType == Character.TYPE) return false;

        if (subType == Byte.TYPE) {
            return true;
        } else if (subType == Short.TYPE) {
            return superType != Byte.TYPE;
        } else if (subType == Integer.TYPE) {
            return superType == Long.TYPE || superType == Double.TYPE;
        } else {
            if (subType == Float.TYPE) {
                return superType == Double.TYPE;
            } else {
                return false;
            }
        }
    }

    public static boolean isMethodInvocationConvertible(Class<?> sourceType, Class<?> targetType) {
        if (targetType.isAssignableFrom(sourceType))
            return true;
        if (sourceType.isPrimitive()) {
            if (targetType.isPrimitive()) {
                return isProperPrimitiveSubtype(sourceType, targetType);
            } else {
                return targetType.isAssignableFrom(WRAPPER_TYPES.get(sourceType));
            }
        } else {
            if (!targetType.isPrimitive()) {
                return false;
            } else {
                Class<?> unboxedCallSiteType = PRIMITIVE_TYPES.get(sourceType);
                return unboxedCallSiteType != null && (unboxedCallSiteType == targetType || isProperPrimitiveSubtype(unboxedCallSiteType, targetType));
            }
        }
    }

    public static boolean isSubtype(Class<?> subType, Class<?> superType) {
        return superType.isAssignableFrom(subType) || superType.isPrimitive() && subType.isPrimitive()
                && isProperPrimitiveSubtype(subType, superType);
    }

    private static boolean isProperPrimitiveSubtype(Class<?> subType, Class<?> superType) {
        if (superType == Boolean.TYPE || subType == Boolean.TYPE) {
            return false;
        }
        if (subType == Byte.TYPE) {
            return superType == Short.TYPE
                    || superType == Integer.TYPE
                    || superType == Long.TYPE
                    || superType == Float.TYPE
                    || superType == Double.TYPE;
        } else if (subType == Short.TYPE || subType == Character.TYPE) {
            return superType == Integer.TYPE
                    || superType == Long.TYPE
                    || superType == Float.TYPE
                    || superType == Double.TYPE;
        } else if (subType == Integer.TYPE) {
            return superType == Long.TYPE
                    || superType == Float.TYPE
                    || superType == Double.TYPE;
        } else if (subType == Long.TYPE) {
            return superType == Float.TYPE
                    || superType == Double.TYPE;
        }

        if (subType == Float.TYPE) {
            return superType == Double.TYPE;
        } else { // subType == Double.TYPE
            return false;
        }
    }

    public static Class<?> getPrimitiveType(Class<?> wrapperType) {
        return PRIMITIVE_TYPES.get(wrapperType);
    }

    public static Class<?> getWrapperType(Class<?> primitiveType) {
        return WRAPPER_TYPES.get(primitiveType);
    }

    public static boolean isWrapperType(Class<?> type) {
        return PRIMITIVE_TYPES.containsKey(type);
    }
}
