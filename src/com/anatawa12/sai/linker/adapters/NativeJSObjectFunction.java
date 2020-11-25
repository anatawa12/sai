package com.anatawa12.sai.linker.adapters;

import com.anatawa12.sai.Context;
import com.anatawa12.sai.Function;
import com.anatawa12.sai.Scriptable;
import jdk.nashorn.api.scripting.JSObject;

public class NativeJSObjectFunction extends NativeJSObject implements Function {
    NativeJSObjectFunction(Scriptable scope, JSObject javaObject) {
        super(scope, javaObject);
    }

    public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        return AdapterContext.wrapToR(
                getBody().call(thisObj, AdapterContext.wrapToNAll(args, cx)),
                scope, cx);
    }

    public Scriptable construct(Context cx, Scriptable scope, Object[] args) {
        return AdapterContext.wrapNewObjectToR(
                getBody().newObject(AdapterContext.wrapToNAll(args, cx)),
                scope, cx);
    }
}
