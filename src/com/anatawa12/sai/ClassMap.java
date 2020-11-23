package com.anatawa12.sai;

import java.util.concurrent.atomic.AtomicReference;

class ClassMap<V> {
    private final ClassValue<AtomicReference<V>> classValue = new ClassValue<AtomicReference<V>>() {
        @Override
        protected AtomicReference<V> computeValue(Class<?> type) {
            return new AtomicReference<>();
        }
    };

    public V get(Class<?> type) {
        return classValue.get(type).get();
    }

    public void put(Class<?> type, V newValue) {
        classValue.get(type).set(newValue);
    }

    public boolean compareAndSet(Class<?> type, V expect, V update) {
        return classValue.get(type).compareAndSet(expect, update);
    }
}
