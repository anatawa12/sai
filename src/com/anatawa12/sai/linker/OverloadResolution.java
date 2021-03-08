package com.anatawa12.sai.linker;

import com.anatawa12.sai.Wrapper;
import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public class OverloadResolution {
    public static final Class<?> nullClass = OverloadResolution.NullClass.class;

    public OverloadResolution() {
    }

    public static <T extends Executable> List<MethodOrConstructor> resolve(Iterable<MethodOrConstructor> methods, Class<?>[] arguments) {
        List<CallableSignature> resolved = resolveCallable(fixSignature(methods), arguments);
        if (resolved.isEmpty()) {
            resolved = resolveCallable(varargSignature(methods), arguments);
        }

        return resolved.stream().map(OverloadResolution.CallableSignature::getExecutable).collect(Collectors.toList());
    }

    public static Class<?>[] types(Object[] arguments) {
        Class<?>[] classes = new Class[arguments.length];

        for (int i = 0; i < arguments.length; ++i) {
            Object arg = arguments[i];
            if (arg instanceof Wrapper) {
                arg = ((Wrapper) arg).unwrap();
            }

            classes[i] = arg == null ? nullClass : arg.getClass();
        }

        return classes;
    }

    private static List<CallableSignature> resolveCallable(Iterable<CallableSignature> methods, Class<?>[] arguments) {
        List<CallableSignature> resolved = new ArrayList<>();
        for (CallableSignature method : methods) {
            if (!method.canApplicable(arguments.length)) continue;
            if (!canApplicable(method, arguments)) continue;

            boolean type2Better = false;

            Iterator<CallableSignature> iter = resolved.iterator();

            while (iter.hasNext()) {
                CallableSignature cur = iter.next();
                switch (compare(method, cur, arguments)) {
                    case Type1Better:
                        // method is better: cur is not one of the best
                        iter.remove();
                        break;
                    case Type2Better:
                        // cur is better: method is not one of the best
                        type2Better = true;
                        break;
                    case Same:
                        // same: nop
                        break;
                }
            }

            if (!type2Better) {
                resolved.add(method);
            }
        }

        return resolved;
    }

    private static boolean canApplicable(CallableSignature method, Class<?>[] arguments) {
        for (int i = 0; i < arguments.length; ++i) {
            if (!canConvert(arguments[i], method.getTypeAt(i))) {
                return false;
            }
        }

        return true;
    }

    private static boolean canConvert(Class<?> from, Class<?> to) {
        return from == nullClass ? !to.isPrimitive() : TypeConvertUtil.canConvert(from, to);
    }

    private static Comparison compare(CallableSignature method1, CallableSignature method2, Class<?>[] arguments) {
        Comparison current = Comparison.Same;

        for (int i = 0; i < arguments.length; ++i) {
            switch (compareType(method1.getTypeAt(i), method2.getTypeAt(i), arguments[i])) {
                case Type1Better:
                    if (current == Comparison.Type2Better)
                        return Comparison.Same;

                    current = Comparison.Type1Better;
                    break;
                case Type2Better:
                    if (current == Comparison.Type1Better)
                        return Comparison.Same;
                    current = Comparison.Type2Better;
                case Same:
            }
        }

        return current;
    }

    private static Comparison compareType(Class<?> type1, Class<?> type2, Class<?> argument) {
        if (type1 == type2) {
            return Comparison.Same;
        } else if (argument == nullClass) {
            if (!type1.isPrimitive()) {
                if (type2.isPrimitive()) {
                    return Comparison.Type1Better;
                }
            } else if (!type2.isPrimitive()) {
                return Comparison.Type2Better;
            }

            return Comparison.Same;
        } else {
            Comparison comparison = TypeCompareRules.compare(argument, type1, type2);
            if (comparison != Comparison.Same) {
                return comparison;
            } else if (TypeUtil.isSubtype(argument, type1)) {
                return Comparison.Type1Better;
            } else {
                return TypeUtil.isSubtype(argument, type2) ? Comparison.Type2Better : Comparison.Same;
            }
        }
    }

    private static Iterable<CallableSignature> fixSignature(Iterable<MethodOrConstructor> iterable) {
        return () -> {
            final Iterator<MethodOrConstructor> iter = iterable.iterator();
            return new Iterator<CallableSignature>() {
                public boolean hasNext() {
                    return iter.hasNext();
                }

                public CallableSignature next() {
                    final MethodOrConstructor value = iter.next();
                    return new CallableSignature() {
                        public boolean canApplicable(int length) {
                            return value.parameterCount() == length;
                        }

                        public Class<?> getTypeAt(int index) {
                            return value.parameterType(index);
                        }

                        public MethodOrConstructor getExecutable() {
                            return value;
                        }
                    };
                }
            };
        };
    }

    private static Iterable<CallableSignature> varargSignature(Iterable<MethodOrConstructor> iterable) {
        return () -> {
            final Iterator<MethodOrConstructor> iter = iterable.iterator();
            return new Iterator<CallableSignature>() {
                public boolean hasNext() {
                    return iter.hasNext();
                }

                public CallableSignature next() {
                    final MethodOrConstructor value = iter.next();
                    return new CallableSignature() {
                        public boolean canApplicable(int length) {
                            if (!value.isVarArgs()) {
                                return false;
                            } else {
                                return length >= value.parameterCount() - 1;
                            }
                        }

                        public Class<?> getTypeAt(int index) {
                            if (index >= value.parameterCount() - 1)
                                return value.parameterType(value.parameterCount() - 1).getComponentType();
                            return value.parameterType(index);
                        }

                        public MethodOrConstructor getExecutable() {
                            return value;
                        }
                    };
                }
            };
        };
    }

    private static class NullClass {
        private NullClass() {
        }
    }

    private interface CallableSignature {
        boolean canApplicable(int var1);

        Class<?> getTypeAt(int var1);

        MethodOrConstructor getExecutable();
    }
}
