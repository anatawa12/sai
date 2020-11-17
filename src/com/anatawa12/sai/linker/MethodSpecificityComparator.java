package com.anatawa12.sai.linker;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class MethodSpecificityComparator {
    public static List<MethodOrConstructor> getMaximallySpecificMethods(
            List<MethodOrConstructor> methods, boolean varArgs, Class<?>[] argTypes) {
        // fast path: if the list contains 0 or 1 elements, that's most specific.
        if (methods.size() <= 1) return methods;

        LinkedList<MethodOrConstructor> maximals = new LinkedList<>();

        for (MethodOrConstructor method : methods) {
            boolean moreSpecific = true;
            Iterator<MethodOrConstructor> iter = maximals.iterator();

            while (iter.hasNext()) {
                MethodOrConstructor mayMax = iter.next();
                switch (compare(method, mayMax, varArgs, argTypes)) {
                    // method is better: mayMax is not most specific
                    case TYPE_1_BETTER:
                        iter.remove();
                        break;
                    // mayMax is better: method is not specific
                    case TYPE_2_BETTER:
                        moreSpecific = false;
                        break;
                    // same: same so mayMax is still may most specific and method may be too.
                    case INDETERMINATE:
                        break;
                    default:
                        throw new AssertionError();
                }
            }

            if (moreSpecific)
                maximals.addLast(method);
        }

        return maximals;
    }

    public static ConversionComparison compare(
            MethodOrConstructor method1, MethodOrConstructor method2, boolean varArgs, Class<?>[] argTypes) {
        int pc1 = method1.parameterCount();
        int pc2 = method2.parameterCount();

        assert varArgs || pc1 == pc2;

        int maxPc = Math.max(Math.max(pc1, pc2), 0);

        ConversionComparison result = ConversionComparison.INDETERMINATE;

        for (int i = 0; i < maxPc; i++) {
            Class<?> class1 = getParameterClass(method1, i, varArgs);
            Class<?> class2 = getParameterClass(method2, i, varArgs);

            // if same type, it's always INDETERMINATE so not changed
            if (class1 == class2) continue;

            Class<?> argType = argTypes != null ? argTypes[i] : null;

            ConversionComparison cmp = compare(class1, class2, argType);

            // if same as current specification, specific is not changed
            if (result == cmp)
                continue;
            // if INDETERMINATE, specific is not changed
            if (cmp == ConversionComparison.INDETERMINATE)
                continue;

            // if for some some argument, 1 is better and for other, 2 is better,
            // that's INDETERMINATE
            if (result != ConversionComparison.INDETERMINATE)
                continue;

            // if result is INDETERMINATE
            result = cmp;
        }

        return result;
    }

    private static ConversionComparison compare(Class<?> c1, Class<?> c2, Class<?> argType) {
        if (argType != null) {
            ConversionComparison compare = LinkerServices.INSTANCE.compareConversion(argType, c1, c2);
            if (compare != ConversionComparison.INDETERMINATE)
                return compare;
        }
        if (TypeUtil.isSubtype(c1, c2)) {
            return ConversionComparison.TYPE_1_BETTER;
        } else if (TypeUtil.isSubtype(c2, c1)) {
            return ConversionComparison.TYPE_2_BETTER;
        } else {
            return ConversionComparison.INDETERMINATE;
        }
    }

    private static Class<?> getParameterClass(MethodOrConstructor t, int i, boolean varArgs) {
        if (varArgs && t.parameterCount() - 1 <= i) return t.parameterType(t.parameterCount() - 1).getComponentType();
        else return t.parameterType(i);
    }
}
