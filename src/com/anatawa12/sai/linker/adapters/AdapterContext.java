package com.anatawa12.sai.linker.adapters;

import com.anatawa12.sai.Context;
import com.anatawa12.sai.ContextAction;
import com.anatawa12.sai.ContextFactory;
import com.anatawa12.sai.ScriptRuntime;
import com.anatawa12.sai.Scriptable;
import com.anatawa12.sai.Wrapper;
import jdk.nashorn.api.scripting.JSObject;

import java.util.function.Consumer;

class AdapterContext {
    private final ContextFactory context;

    public AdapterContext(ContextFactory context) {
        this.context = context;
    }

    <T> T withContext(ContextAction<T> block) {
        Context oldContext = Context.getCurrentContext();
        boolean contextChanged = oldContext.getFactory() != context;
        if (contextChanged) {
            Context.exit();
            context.enterContext(null);
        }
        try {
            return block.run(Context.getCurrentContext());
        } finally {
            if (contextChanged) {
                Context.exit();
                oldContext.getFactory().enterContext(oldContext);
            }
        }
    }

    void withContextV(Consumer<Context> block) {
        withContext(context -> {
            block.accept(context);
            return null;
        });
    }

    static Scriptable wrapNewObjectToR(Object obj, Context context) {
        return obj == null ? null : context.getWrapFactory().wrapNewObject(context, scope(context), obj);
    }

    static Scriptable wrapNewObjectToR(Object obj, Scriptable scope, Context context) {
        if (obj == null) return null;
        return context.getWrapFactory().wrapNewObject(context, scope, obj);
    }

    static Object wrapToR(Object obj, Context context) {
        // neutralize wrap factory java primitive wrap feature
        if (!(obj instanceof String || obj instanceof Number
                || obj instanceof Boolean)) {
            return context.getWrapFactory().wrap(context, scope(context), obj, null);
        } else {
            return obj;
        }
    }

    static Object wrapToR(Object obj, Scriptable scope, Context context) {
        // neutralize wrap factory java primitive wrap feature
        if (!(obj instanceof String || obj instanceof Number
                || obj instanceof Boolean)) {
            return context.getWrapFactory().wrap(context, scope, obj, null);
        } else {
            return obj;
        }
    }

    static Object[] wrapToRAll(Object[] objs, Context context) {
        Object[] result = new Object[objs.length];
        for (int i = 0; i < result.length; i++) {
            Object obj = objs[i];
            result[i] = wrapToR(obj, context);
        }
        return result;
    }

    static Object[] wrapToRAll(Object[] objs, Scriptable scope, Context context) {
        Object[] result = new Object[objs.length];
        for (int i = 0; i < result.length; i++) {
            Object obj = objs[i];
            result[i] = wrapToR(obj, scope, context);
        }
        return result;
    }

    static Object wrapToN(Object obj, Context context) {
        obj = obj instanceof Wrapper ? ((Wrapper)obj).unwrap() : obj;
        if (obj == null) {
            return null;
        } else if (obj instanceof JSObject) {
            return obj;
        } else {
            return obj instanceof Scriptable ? new ScriptableMirror((Scriptable)obj, context) : obj;
        }
    }

    static Object[] wrapToNAll(Object[] objs, Context context) {
        Object[] result = new Object[objs.length];
        for (int i = 0; i < result.length; i++) {
            Object obj = objs[i];
            result[i] = wrapToN(obj, context);
        }
        return result;
    }

    static Scriptable scope(Context context) {
        return ScriptRuntime.getTopCallScope(context);
    }
}
