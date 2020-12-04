package com.anatawa12.sai;

import java.util.List;

public class FileNameMapping {
    // inclusive
    public int lastLine;
    public final List<Parser.LineNoMapping> mappings;

    public FileNameMapping(int lastLine, List<Parser.LineNoMapping> mappings) {
        this.lastLine = lastLine;
        this.mappings = mappings;
    }

    public Parser.LineNoMapping findMapping(int line) {
        if (this.lastLine < line) return null;
        if (line < mappings.get(0).startLineNo) return null;

        // inclusive
        int intervalStart = 0;
        // exclusive
        int intervalEnd = mappings.size();

        //二分探索のつもり.
        while (intervalEnd - intervalStart != 1) {
            int cur = intervalStart + (intervalEnd - intervalStart) / 2;
            Parser.LineNoMapping mapping = mappings.get(cur);
            if (mapping.startLineNo < line) {
                // cur or after
                intervalStart = cur;
            } else {
                // before cur
                intervalEnd = cur;
            }
        }

        return mappings.get(intervalStart);
    }

    public StackTraceElement modify(StackTraceElement element) {
        Parser.LineNoMapping mapping = findMapping(element.getLineNumber());
        if (mapping == null) return element;
        return new StackTraceElement(
                element.getClassName(),
                element.getMethodName(),
                mapping.fileName,
                mapping.mapLineNumber(element.getLineNumber())
        );
    }

    @Override
    public String toString() {
        return "FileNameMapping{" +
                "lastLine=" + lastLine +
                ", mappings=" + mappings +
                '}';
    }
}
