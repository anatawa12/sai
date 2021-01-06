package com.anatawa12.sai.linker;

import com.anatawa12.sai.Context;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OverloadedMethod {
    private final Map<ClassList, CacheEntry> argTypesToMethods = new ConcurrentHashMap<>();
    private final String name;
    private final ArrayList<MethodOrConstructor> fixArgMethods;
    private final ArrayList<MethodOrConstructor> varArgMethods;

    OverloadedMethod(List<MethodOrConstructor> methodHandles, String name, int parameterCount) {
        this.name = name;
        this.fixArgMethods = new ArrayList<>(methodHandles.size());
        this.varArgMethods = new ArrayList<>(methodHandles.size());

        for (MethodOrConstructor method : methodHandles) {
            if (method.isVarArgs()) {
                if (parameterCount == method.parameterCount())
                    this.fixArgMethods.add(method);
                this.varArgMethods.add(method);
            } else {
                this.fixArgMethods.add(method);
            }
        }

        this.fixArgMethods.trimToSize();
        this.varArgMethods.trimToSize();
    }

    public int methodsCount() {
        return this.fixArgMethods.size() + this.varArgMethods.size();
    }

    public MethodOrConstructor theOnlyMethod() {
        if (methodsCount() != 1)
            throw new IllegalArgumentException("there's two or more or no elements");
        if (fixArgMethods.isEmpty())
            return varArgMethods.get(0);
        return fixArgMethods.get(0);
    }

    public MethodOrConstructor selectMethod(ClassList classes) {
        CacheEntry cacheEntry = this.argTypesToMethods.get(classes);
        if (cacheEntry != null) return cacheEntry.get(this, classes);

        List<MethodOrConstructor> methods
                = classes.getMaximallySpecifics(this.fixArgMethods, false);
        if (methods.isEmpty()) {
            methods = classes.getMaximallySpecifics(this.varArgMethods, true);
        }

        switch(methods.size()) {
            case 0:
                cacheEntry = NoSuchMethodEntry.INSTANCE;
                break;
            case 1:
                cacheEntry = new MethodEntry(methods.get(0));
                break;
            default:
                cacheEntry = new AmbiguousMethodEntry(methods);
                break;
        }

        this.argTypesToMethods.put(classes, cacheEntry);

        return cacheEntry.get(this, classes);
    }

    private RuntimeException throwNoSuchMethod(Class<?>[] argTypes) {
        if (this.varArgMethods.isEmpty()) {
            return Context.reportRuntimeError("None of"
                    + " the fixed arity signatures " + getSignatureList(this.fixArgMethods)
                    + " of the method " + this.name
                    + " match the argument types " + argTypesString(argTypes));
        } else {
            return Context.reportRuntimeError("None of"
                    + " the fixed arity signatures " + getSignatureList(this.fixArgMethods)
                    + " or the variable arity signatures " + getSignatureList(this.varArgMethods)
                    + " of the method " + this.name
                    + " match the argument types " + argTypesString(argTypes));
        }
    }

    private RuntimeException throwAmbiguousMethod(Class<?>[] argTypes, List<MethodOrConstructor> methods) {
        String arityKind = methods.get(0).isVarArgs() ? "variable" : "fixed";
        return Context.reportRuntimeError("Can't unambiguously select"
                + " between " + arityKind + " arity"
                + " signatures " + getSignatureList(methods)
                + " of the method " + this.name
                + " for argument types " + argTypesString(argTypes));
    }

    private static String argTypesString(Class<?>[] classes) {
        StringBuilder b = new StringBuilder().append('[');
        appendTypes(b, classes, false);
        return b.append(']').toString();
    }

    private static String getSignatureList(List<MethodOrConstructor> methods) {
        StringBuilder b = new StringBuilder().append('[');
        Iterator<MethodOrConstructor> it = methods.iterator();
        if (it.hasNext()) {
            appendSig(b, it.next());

            while(it.hasNext()) {
                appendSig(b.append(", "), it.next());
            }
        }

        return b.append(']').toString();
    }

    private static void appendSig(StringBuilder b, MethodOrConstructor m) {
        b.append('(');
        appendTypes(b, m.parameterArray(), m.isVarArgs());
        b.append(')');
    }

    private static void appendTypes(StringBuilder b, Class<?>[] classes, boolean varArg) {
        if (!varArg) {
            if (classes.length >= 1) {
                b.append(classes[0].getCanonicalName());

                for(int i = 1; i < classes.length; ++i) {
                    b.append(", ");
                    b.append(classes[i].getCanonicalName());
                }
            }
        } else {
            for(int i = 0; i < classes.length - 1; ++i) {
                b.append(classes[i].getCanonicalName());
                b.append(", ");
            }

            b.append(classes[classes.length - 1].getComponentType().getCanonicalName());
            b.append("...");
        }
    }

    private abstract static class CacheEntry {
        abstract MethodOrConstructor get(OverloadedMethod method, ClassList classes);
    }

    private static class NoSuchMethodEntry extends CacheEntry {
        static NoSuchMethodEntry INSTANCE = new NoSuchMethodEntry();

        private NoSuchMethodEntry() {}

        @Override
        MethodOrConstructor get(OverloadedMethod method, ClassList classes) {
            throw method.throwNoSuchMethod(classes.getClasses());
        }
    }

    private static class MethodEntry extends CacheEntry {
        private final MethodOrConstructor method;

        private MethodEntry(MethodOrConstructor method) {
            this.method = method;
        }

        @Override
        MethodOrConstructor get(OverloadedMethod method, ClassList classes) {
            return this.method;
        }
    }

    private static class AmbiguousMethodEntry extends CacheEntry {
        private final List<MethodOrConstructor> methods;

        private AmbiguousMethodEntry(List<MethodOrConstructor> methods) {
            this.methods = methods;
        }

        @Override
        MethodOrConstructor get(OverloadedMethod method, ClassList classes) {
            throw method.throwAmbiguousMethod(classes.getClasses(), methods);
        }
    }
}
