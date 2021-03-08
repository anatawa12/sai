package com.anatawa12.sai.linker;

import com.anatawa12.sai.Context;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MethodResolveCache implements Serializable {
    private static final long serialVersionUID = 0;
    private final List<MethodOrConstructor> methods;
    private final String name;
    private transient final Map<List<Class<?>>, List<MethodOrConstructor>> cache = new HashMap<>();

    public MethodResolveCache(List<MethodOrConstructor> methods) {
        this.methods = Collections.unmodifiableList(new ArrayList<>(methods));
        this.name = methods.iterator().next().name();
    }

    public MethodResolveCache(List<MethodOrConstructor> methods, String name) {
        this.methods = Collections.unmodifiableList(new ArrayList<>(methods));
        this.name = name;
    }

    public List<MethodOrConstructor> getMethods() {
        return this.methods;
    }

    public String getName() {
        return this.name;
    }

    public int size() {
        return this.methods.size();
    }

    public List<MethodOrConstructor> getResolvedMethods(Class<?>[] arguments) {
        List<Class<?>> typeList = Arrays.asList(arguments);
        List<MethodOrConstructor> resolved = this.cache.get(typeList);
        if (resolved == null) {
            resolved = OverloadResolution.resolve(this.methods, arguments);
            this.cache.put(typeList, resolved);
        }

        return resolved;
    }

    public MethodOrConstructor getResolved(Object[] arguments) {
        Class<?>[] types = OverloadResolution.types(arguments);
        List<MethodOrConstructor> resolved = this.getResolvedMethods(types);
        if (resolved.isEmpty()) {
            throw this.throwNoSuchMethod(types);
        } else if (resolved.size() != 1) {
            throw this.throwAmbiguousMethod(types);
        } else {
            return resolved.get(0);
        }
    }

    public List<MethodOrConstructor> getCallableMethodsByArgumentLength(int size) {
        return this.methods.stream()
                .filter((f) -> f.isVarArgs() ? size >= f.parameterCount() - 1 : size == f.parameterCount())
                .collect(Collectors.toList());
    }

    private RuntimeException throwNoSuchMethod(Class<?>[] argTypes) {
        return Context.reportRuntimeError("None of the method " + this.name + " " +
                "match the argument types " + this.typesToString(argTypes));
    }

    private RuntimeException throwAmbiguousMethod(Class<?>[] argTypes) {
        return Context.reportRuntimeError("Can't unambiguously select signatures of the method " + this.name + " " +
                "for argument types " + this.typesToString(argTypes));
    }

    private String typesToString(Class<?>[] argTypes) {
        return Arrays.stream(argTypes).map(Class::getCanonicalName)
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
