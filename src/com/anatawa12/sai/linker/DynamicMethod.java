package com.anatawa12.sai.linker;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicMethod implements Serializable {
    private static final long serialVersionUID = 440253398627762620L;
    private final LinkedList<MethodOrConstructor> methods;
    private final String name;
    private transient final Map<Integer, OverloadedMethod> cache = new ConcurrentHashMap<>();

    public DynamicMethod(LinkedList<MethodOrConstructor> methods, String name) {
        this.methods = methods;
        this.name = name;
    }

    public OverloadedMethod getOverloads(int argc) {
        OverloadedMethod method = cache.get(argc);
        if (method == null) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            final List<MethodOrConstructor> invokables = (List) methods.clone();
            invokables.removeIf(m -> !isApplicableDynamically(argc, m));

            method = new OverloadedMethod(invokables, name, argc);

            cache.put(argc, method);
        }

        return method;
    }

    public MethodOrConstructor getInvocation(ClassList classes) {
        return getOverloads(classes.size()).selectMethod(classes);
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
