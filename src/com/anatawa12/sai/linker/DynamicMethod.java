package com.anatawa12.sai.linker;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicMethod {
    private final LinkedList<MethodOrConstructor> methods;
    private final String name;
    private final Map<Integer, OverloadedMethod> cache = new ConcurrentHashMap<>();

    public DynamicMethod(LinkedList<MethodOrConstructor> methods, String name) {
        this.methods = methods;
        this.name = name;
    }

    public MethodOrConstructor getInvocation(ClassList classes) {
        OverloadedMethod method = cache.get(classes.size());
        if (method == null) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            final List<MethodOrConstructor> invokables = (List) methods.clone();
            invokables.removeIf(m -> !isApplicableDynamically(classes.size(), m));

            method = new OverloadedMethod(invokables, name, classes.size());

            cache.put(classes.size(), method);
        }

        return method.selectMethod(classes);
    }

    private static boolean isApplicableDynamically(
            int parameterCount,
            MethodOrConstructor method) {
        boolean varArgs = method.isVarArgs();
        int fixedArgLen = method.parameterCount() - (varArgs ? 1 : 0);

        return varArgs
                ? fixedArgLen <= parameterCount
                : fixedArgLen == parameterCount;
    }

    public LinkedList<MethodOrConstructor> getMethods() {
        return methods;
    }

    public boolean isEmpty() {
        return methods.isEmpty();
    }

    public int size() {
        return methods.size();
    }
}
