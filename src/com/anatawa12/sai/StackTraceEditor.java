package com.anatawa12.sai;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class StackTraceEditor {
    private static final ThreadLocal<ThreadedData> editedExceptions
            = ThreadLocal.withInitial(ThreadedData::new);
    private static final Map<String, FileNameMapping> mappingMap = new ConcurrentHashMap<>();

    @SuppressWarnings("unused") // used by generated source
    public static void addMapping(Object remover, String sourceName, FileNameMapping mapping) {
        addMapping(sourceName, mapping);

        // clean when remover is released
        new Cleaner.PhantomAction<Object>(remover) {
            @Override
            public void run() {
                mappingMap.remove(sourceName);
            }
        };
    }

    public static void addMapping(String sourceName, FileNameMapping mapping) {
        FileNameMapping oldValue = mappingMap.putIfAbsent(sourceName, mapping);
        if (oldValue != null && !oldValue.equals(mapping))
            throw new IllegalStateException("the mapping for '" + sourceName + "' is already exists");
    }

    @SuppressWarnings("unused") // used by generated source
    public static void editTrace(Throwable throwable) {
        ThreadedData threaded = StackTraceEditor.editedExceptions.get();
        ThreadedData.WeakIdentityKey<Throwable> key = threaded.createKey(throwable);
        if (threaded.contains(key)) {
            key.clear();
            return;
        }
        StackTraceElement[] trace = throwable.getStackTrace();
        boolean modified = editTrace(trace);
        if (modified) {
            throwable.setStackTrace(trace);
        }
        threaded.add(key);
        if (throwable.getCause() != null) {
            editTrace(throwable.getCause());
        }
        for (Throwable suppressed : throwable.getSuppressed()) {
            editTrace(suppressed);
        }
    }

    public static boolean editTrace(StackTraceElement[] trace) {
        boolean modified = false;
        for (int i = 0; i < trace.length; i++) {
            StackTraceElement edited = edit(trace[i]);
            if (edited != trace[i]) {
                modified = true;
                trace[i] = edited;
            }
        }
        return modified;
    }

    private static StackTraceElement edit(StackTraceElement stackTraceElement) {
        if (stackTraceElement.getFileName() == null) return stackTraceElement;
        FileNameMapping mapping = mappingMap.get(stackTraceElement.getFileName());
        if (mapping != null) {
            return mapping.modify(stackTraceElement);
        }
        return stackTraceElement;
    }

    private static class ThreadedData {
        private final Set<WeakIdentityKey<Throwable>> set = Collections.newSetFromMap(new HashMap<>());
        private final ReferenceQueue<Throwable> queue = new ReferenceQueue<>();

        @SuppressWarnings("SuspiciousMethodCalls")
        private void clean() {
            Reference<? extends Throwable> ref;
            while ((ref = queue.poll()) != null) {
                set.remove(ref);
            }
        }

        public WeakIdentityKey<Throwable> createKey(Throwable throwable) {
            return new WeakIdentityKey<>(throwable, queue);
        }

        public boolean contains(WeakIdentityKey<Throwable> key) {
            clean();
            return set.contains(key);
        }

        public boolean add(WeakIdentityKey<Throwable> key) {
            clean();
            return set.add(key);
        }

        static class WeakIdentityKey<T> extends WeakReference<T> {
            private final int hash;

            public WeakIdentityKey(T referent, ReferenceQueue<? super T> queue) {
                super(referent, queue);
                hash = System.identityHashCode(referent);
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                WeakIdentityKey<?> that = (WeakIdentityKey<?>) o;
                Object a = that.get();
                Object b = this.get();
                return a != null && a == b;
            }

            @Override
            public int hashCode() {
                return hash;
            }
        }
    }
}
