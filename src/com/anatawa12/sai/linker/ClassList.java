package com.anatawa12.sai.linker;

import java.util.ArrayList;
import java.util.Arrays;
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
        return MethodSpecificityComparator.getMaximallySpecificMethods(methods, varArgs, getClasses());
    }

    public int size() {
        return classes.length;
    }

    private static class NullClass {

    }
}
