package com.anatawa12.sai;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class StackTraceEditor {
    private static final Set<Throwable> editedExceptions = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<String, FileNameMapping> mappingMap = new HashMap<>();

    public static void addMapping(String sourceName, FileNameMapping mapping) {
        FileNameMapping oldValue = mappingMap.putIfAbsent(sourceName, mapping);
        if (oldValue != null && oldValue != mapping)
            throw new IllegalStateException("the mapping for '" + sourceName + "' is already exists");
    }

    public static void editTrace(Throwable throwable) {
        if (editedExceptions.contains(throwable)) return;
        StackTraceElement[] trace = throwable.getStackTrace();
        boolean modified = false;
        for (int i = 0; i < trace.length; i++) {
            StackTraceElement edited = edit(trace[i]);
            if (edited != trace[i]) {
                modified = true;
                trace[i] = edited;
            }
        }
        if (modified) {
            throwable.setStackTrace(trace);
        }
        editedExceptions.add(throwable);
        if (throwable.getCause() != null) {
            editTrace(throwable.getCause());
        }
        for (Throwable suppressed : throwable.getSuppressed()) {
            editTrace(suppressed);
        }
    }

    private static StackTraceElement edit(StackTraceElement stackTraceElement) {
        FileNameMapping mapping = mappingMap.get(stackTraceElement.getFileName());
        if (mapping != null) {
            return mapping.modify(stackTraceElement);
        }
        return stackTraceElement;
    }
}
