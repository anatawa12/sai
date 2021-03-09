package com.anatawa12.sai.linker;

import com.anatawa12.sai.NativeArray;
import com.anatawa12.sai.Scriptable;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Queue;

public class TypeCompareRules {
    private static final TypeCompareRule[] rules = new TypeCompareRule[]{
            new TypeCompareRule(NativeArray.class, List.class, Collection.class, Queue.class, Deque.class),
            new TypeCompareRule(NativeArray.class, Class::isArray),

            new TypeCompareRule(Scriptable.class::isAssignableFrom, Class::isInterface),

            new TypeCompareRule((source, target) -> source == getWrapperTypeOrSelf(target)),
            new TypeCompareRule((s) -> Number.class.isAssignableFrom(getWrapperTypeOrSelf(s)),
                    (t) -> Number.class.isAssignableFrom(getWrapperTypeOrSelf(t))),
            new TypeCompareRule((s) -> Number.class.isAssignableFrom(getWrapperTypeOrSelf(s)),
                    (t) -> Character.class == getWrapperTypeOrSelf(t)),

            new TypeCompareRule((c) -> c == String.class || c == Boolean.class || Number.class.isAssignableFrom(c),
                    (self, other) -> TypeUtil.isMethodInvocationConvertible(getPrimitiveTypeOrSelf(other), getPrimitiveTypeOrSelf(self))),
            new TypeCompareRule((c) -> c == String.class || c == Boolean.class || Number.class.isAssignableFrom(c), String.class),

            new TypeCompareRule(TypeUtil::isMethodInvocationConvertible),
    };

    public TypeCompareRules() {
    }

    private static Class<?> getWrapperTypeOrSelf(Class<?> type) {
        Class<?> wrapper = TypeUtil.getWrapperType(type);
        return wrapper == null ? type : wrapper;
    }

    private static Class<?> getPrimitiveTypeOrSelf(Class<?> type) {
        Class<?> primitive = TypeUtil.getPrimitiveType(type);
        return primitive == null ? type : primitive;
    }

    public static Comparison compare(Class<?> source, Class<?> type1, Class<?> type2) {
        if (type1 == type2) return Comparison.Same;

        for (TypeCompareRule rule : rules) {
            boolean test1;
            boolean test2;
            if (rule.condition != null) {
                test1 = rule.condition.test(source, type1);
                test2 = rule.condition.test(source, type2);
            } else {
                if (!rule.sourceTypeCondition.test(source)) {
                    continue;
                }

                test1 = rule.targetTypeCondition.test(type1, type2);
                test2 = rule.targetTypeCondition.test(type2, type1);
            }

            if (test1) {
                if (!test2) {
                    return Comparison.Type1Better;
                }
            } else if (test2) {
                return Comparison.Type2Better;
            }
        }

        return Comparison.Same;
    }
}
