package com.anatawa12.sai.linker;

import com.anatawa12.sai.Wrapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class ClassList {
    public static final Class<?> NULL_CLASS = NullClass.class;

    private final Class<?>[] classes;

    public ClassList(Class<?>[] classes) {
        this.classes = classes;
    }

    public static ClassList fromArgs(Object[] args) {
        Class<?>[] argTypes = new Class[args.length];

        for(int i = 0; i < argTypes.length; ++i) {
            Object arg = args[i];
            if (arg instanceof Wrapper) arg = ((Wrapper) arg).unwrap();
            argTypes[i] = arg == null ? NULL_CLASS : arg.getClass();
        }

        return new ClassList(argTypes);
    }

    public Class<?>[] getClasses() {
        return classes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClassList classList = (ClassList) o;
        return Arrays.equals(classes, classList.classes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(classes);
    }

    public List<MethodOrConstructor> getMaximallySpecifics(ArrayList<MethodOrConstructor> methods, boolean varArgs) {
        return MethodSpecificityComparator.getMaximallySpecificMethods(
                getApplicableMethods(methods, varArgs),
                varArgs, getClasses());
    }

    LinkedList<MethodOrConstructor> getApplicableMethods(final List<MethodOrConstructor> methods, final boolean varArg) {
        final LinkedList<MethodOrConstructor> list = new LinkedList<>();
        for(final MethodOrConstructor member: methods) {
            if(isApplicable(member, varArg)) {
                list.add(member);
            }
        }
        return list;
    }

    private boolean isApplicable(final MethodOrConstructor method, final boolean varArg) {
        final Class<?>[] formalTypes = method.parameterArray();
        final int cl = classes.length;
        final int fl = formalTypes.length - (varArg ? 1 : 0);
        if(varArg) {
            if(classes.length < fl) {
                return false;
            }
        } else {
            if(classes.length != fl) {
                return false;
            }
        }
        for(int i = 0; i < fl; ++i) {
            if(!canConvert(classes[i], formalTypes[i])) {
                return false;
            }
        }
        if(varArg) {
            final Class<?> varArgType = formalTypes[fl].getComponentType();
            for(int i = fl; i < cl; ++i) {
                if(!canConvert(classes[i], varArgType)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean canConvert(final Class<?> from, final Class<?> to) {
        if(from == NULL_CLASS) {
            return !to.isPrimitive();
        }
        return LinkerServices.INSTANCE.canConvert(from, to);
    }

    public int size() {
        return classes.length;
    }

    private static class NullClass {

    }
}
