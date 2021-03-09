package com.anatawa12.sai.linker;

import java.util.Arrays;
import java.util.HashSet;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class TypeCompareRule {
    final BiPredicate<Class<?>, Class<?>> condition;
    final Predicate<Class<?>> sourceTypeCondition;
    final BiPredicate<Class<?>, Class<?>> targetTypeCondition;

    public TypeCompareRule(Class<?> sourceType, Class<?>... targetTypes) {
        this((c) -> c == sourceType, new HashSet<>(Arrays.asList(targetTypes))::contains);
    }

    public TypeCompareRule(Class<?> sourceType, Predicate<Class<?>> targetTypeCondition) {
        this((c) -> c == sourceType, targetTypeCondition);
    }

    public TypeCompareRule(Predicate<Class<?>> sourceTypeCondition, Class<?>... targetTypes) {
        this(sourceTypeCondition, new HashSet<>(Arrays.asList(targetTypes))::contains);
    }

    public TypeCompareRule(Predicate<Class<?>> sourceTypeCondition, Predicate<Class<?>> targetTypeCondition) {
        this.condition = null;
        this.sourceTypeCondition = sourceTypeCondition;
        this.targetTypeCondition = (self, other) -> targetTypeCondition.test(self);
    }

    public TypeCompareRule(Predicate<Class<?>> sourceTypeCondition, BiPredicate<Class<?>, Class<?>> targetTypeCondition) {
        this.condition = null;
        this.sourceTypeCondition = sourceTypeCondition;
        this.targetTypeCondition = targetTypeCondition;
    }

    public TypeCompareRule(BiPredicate<Class<?>, Class<?>> condition) {
        this.condition = condition;
        this.sourceTypeCondition = null;
        this.targetTypeCondition = null;
    }
}
