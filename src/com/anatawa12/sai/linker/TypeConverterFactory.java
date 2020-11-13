package com.anatawa12.sai.linker;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.util.LinkedList;
import java.util.List;

import com.anatawa12.sai.Undefined;
import com.anatawa12.sai.linker.ConversionComparison;

public class TypeConverterFactory {
    private final GuardingTypeConverterFactory[] factories;
    private final ConversionComparator[] comparators;

    private final ClassValue<ClassValue<MethodHandle>> converterMap = new ClassValue<ClassValue<MethodHandle>>() {
        @Override
        protected ClassValue<MethodHandle> computeValue(final Class<?> sourceType) {
            return new ClassValue<MethodHandle>() {
                @Override
                protected MethodHandle computeValue(final Class<?> targetType) {
                    try {
                        return createConverter(sourceType, targetType);
                    } catch (final RuntimeException e) {
                        throw e;
                    } catch (final Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            };
        }
    };

    private final ClassValue<ClassValue<MethodHandle>> converterIdentityMap = new ClassValue<ClassValue<MethodHandle>>() {
        @Override
        protected ClassValue<MethodHandle> computeValue(final Class<?> sourceType) {
            return new ClassValue<MethodHandle>() {
                @Override
                protected MethodHandle computeValue(final Class<?> targetType) {
                    if(!canAutoConvert(sourceType, targetType) || useConvertorExtraCondition(sourceType, targetType)) {
                        final MethodHandle converter = getCacheableTypeConverter(sourceType, targetType);
                        if(converter != IDENTITY_CONVERSION) {
                            return converter;
                        }
                    }
                    return IDENTITY_CONVERSION.asType(MethodType.methodType(targetType, sourceType));
                }
            };
        }
    };

    // see RhinoLinker
    private boolean useConvertorExtraCondition(Class<?> sourceType, Class<?> targetType) {
        if (targetType == Object.class && Number.class.isAssignableFrom(sourceType)) return true;
        return false;
    }

    private final ClassValue<ClassValue<Boolean>> canConvert = new ClassValue<ClassValue<Boolean>>() {
        @Override
        protected ClassValue<Boolean> computeValue(final Class<?> sourceType) {
            return new ClassValue<Boolean>() {
                @Override
                protected Boolean computeValue(final Class<?> targetType) {
                    try {
                        return getTypeConverterNull(sourceType, targetType) != null;
                    } catch (final RuntimeException e) {
                        throw e;
                    } catch (final Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            };
        }
    };

    public TypeConverterFactory(final Iterable<? extends GuardingTypeConverterFactory> factories) {
        final List<GuardingTypeConverterFactory> l = new LinkedList<>();
        final List<ConversionComparator> c = new LinkedList<>();
        for(final GuardingTypeConverterFactory factory: factories) {
            l.add(factory);
            if(factory instanceof ConversionComparator) {
                c.add((ConversionComparator)factory);
            }
        }
        this.factories = l.toArray(new GuardingTypeConverterFactory[0]);
        this.comparators = c.toArray(new ConversionComparator[0]);
    }

    public MethodHandle asType(final MethodHandle handle, final MethodType fromType) {
        MethodHandle newHandle = handle;
        final MethodType toType = newHandle.type();
        final int l = toType.parameterCount();
        if(l != fromType.parameterCount()) {
            throw new WrongMethodTypeException("Parameter counts differ: " + handle.type() + " vs. " + fromType);
        }
        int pos = 0;
        final List<MethodHandle> converters = new LinkedList<>();
        for(int i = 0; i < l; ++i) {
            final Class<?> fromParamType = fromType.parameterType(i);
            final Class<?> toParamType = toType.parameterType(i);
            if(canAutoConvert(fromParamType, toParamType)) {
                newHandle = applyConverters(newHandle, pos, converters);
            } else {
                final MethodHandle converter = getTypeConverterNull(fromParamType, toParamType);
                if(converter != null) {
                    if(converters.isEmpty()) {
                        pos = i;
                    }
                    converters.add(converter);
                } else {
                    newHandle = applyConverters(newHandle, pos, converters);
                }
            }
        }
        newHandle = applyConverters(newHandle, pos, converters);

        // Convert return type
        final Class<?> fromRetType = fromType.returnType();
        final Class<?> toRetType = toType.returnType();
        if(fromRetType != Void.TYPE && toRetType != Void.TYPE) {
            if(!canAutoConvert(toRetType, fromRetType)) {
                final MethodHandle converter = getTypeConverterNull(toRetType, fromRetType);
                if(converter != null) {
                    newHandle = MethodHandles.filterReturnValue(newHandle, converter);
                }
            }
        }

        // Give change to automatic conversion strategy, if one is present.
        final MethodHandle autoConvertedHandle = unboxReturnType(newHandle, fromType);

        // Do a final asType for any conversions that remain.
        return autoConvertedHandle.asType(fromType);
    }

    private static MethodHandle applyConverters(final MethodHandle handle, final int pos, final List<MethodHandle> converters) {
        if(converters.isEmpty()) {
            return handle;
        }
        final MethodHandle newHandle =
                MethodHandles.filterArguments(handle, pos, converters.toArray(new MethodHandle[0]));
        converters.clear();
        return newHandle;
    }

    public boolean canConvert(final Class<?> from, final Class<?> to) {
        return canAutoConvert(from, to) || canConvert.get(from).get(to);
    }

    public ConversionComparison compareConversion(final Class<?> sourceType, final Class<?> targetType1, final Class<?> targetType2) {
        for(final ConversionComparator comparator: comparators) {
            final ConversionComparison result = comparator.compareConversion(sourceType, targetType1, targetType2);
            if(result != ConversionComparison.INDETERMINATE) {
                return result;
            }
        }
        if(TypeUtil.isMethodInvocationConvertible(sourceType, targetType1)) {
            if(!TypeUtil.isMethodInvocationConvertible(sourceType, targetType2)) {
                return ConversionComparison.TYPE_1_BETTER;
            }
        } else if(TypeUtil.isMethodInvocationConvertible(sourceType, targetType2)) {
            return ConversionComparison.TYPE_2_BETTER;
        }
        return ConversionComparison.INDETERMINATE;
    }

    private static boolean canAutoConvert(final Class<?> fromType, final Class<?> toType) {
        return TypeUtil.isMethodInvocationConvertible(fromType, toType);
    }

    private MethodHandle getCacheableTypeConverterNull(final Class<?> sourceType, final Class<?> targetType) {
        final MethodHandle converter = getCacheableTypeConverter(sourceType, targetType);
        return converter == IDENTITY_CONVERSION ? null : converter;
    }

    private MethodHandle getTypeConverterNull(final Class<?> sourceType, final Class<?> targetType) {
        try {
            return getCacheableTypeConverterNull(sourceType, targetType);
        } catch(final NotCacheableConverter e) {
            return e.converter;
        }
    }

    private MethodHandle getCacheableTypeConverter(final Class<?> sourceType, final Class<?> targetType) {
        return converterMap.get(sourceType).get(targetType);
    }

    public MethodHandle getTypeConverter(final Class<?> sourceType, final Class<?> targetType) {
        try {
            return converterIdentityMap.get(sourceType).get(targetType);
        } catch(final NotCacheableConverter e) {
            return e.converter;
        }
    }

    private MethodHandle createConverter(final Class<?> sourceType, final Class<?> targetType) throws Exception {
        final MethodType type = MethodType.methodType(targetType, sourceType);
        final MethodHandle identity = IDENTITY_CONVERSION.asType(type);
        MethodHandle last = identity;
        boolean cacheable = true;
        for(int i = factories.length; i-- > 0;) {
            final GuardedTypeConversion next = factories[i].convertToType(sourceType, targetType);
            if(next != null) {
                cacheable = cacheable && next.isCacheable();
                final GuardedInvocation conversionInvocation = next.getConversionInvocation();
                conversionInvocation.assertType(type);
                last = conversionInvocation.compose(last);
            }
        }
        if(last == identity) {
            return IDENTITY_CONVERSION;
        }
        if(cacheable) {
            return last;
        }
        throw new NotCacheableConverter(last);
    }

    private static final MethodHandle IDENTITY_CONVERSION = MethodHandles.identity(Object.class);

    @SuppressWarnings("serial")
    private static class NotCacheableConverter extends RuntimeException {
        final MethodHandle converter;

        NotCacheableConverter(final MethodHandle converter) {
            super("", null, false, false);
            this.converter = converter;
        }
    }

    private static final MethodHandle VOID_TO_OBJECT = MethodHandles.constant(Object.class, Undefined.instance);

    private static MethodHandle unboxReturnType(final MethodHandle target, final MethodType newType) {
        final MethodType targetType = target.type();
        final Class<?> oldReturnType = targetType.returnType();
        final Class<?> newReturnType = newType.returnType();
        if (TypeUtil.isWrapperType(oldReturnType)) {
            if (newReturnType.isPrimitive()) {
                assert TypeUtil.isMethodInvocationConvertible(oldReturnType, newReturnType);
                return MethodHandles.explicitCastArguments(target, targetType.changeReturnType(newReturnType));
            }
        } else if (oldReturnType == void.class && newReturnType == Object.class) {
            return MethodHandles.filterReturnValue(target, VOID_TO_OBJECT);
        }
        return target;
    }
}
