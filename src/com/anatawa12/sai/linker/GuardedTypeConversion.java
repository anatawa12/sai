package com.anatawa12.sai.linker;

public class GuardedTypeConversion {
    private final GuardedInvocation conversionInvocation;
    private final boolean cacheable;

    public GuardedTypeConversion(final GuardedInvocation conversionInvocation, final boolean cacheable) {
        this.conversionInvocation = conversionInvocation;
        this.cacheable = cacheable;
    }

    public GuardedInvocation getConversionInvocation() {
        return conversionInvocation;
    }

    /**
     * Check if invocation is cacheable
     * @return true if cacheable, false otherwise
     */
    public boolean isCacheable() {
        return cacheable;
    }
}
