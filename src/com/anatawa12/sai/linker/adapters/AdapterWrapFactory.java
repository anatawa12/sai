package com.anatawa12.sai.linker.adapters;

import com.anatawa12.sai.Scriptable;
import jdk.nashorn.api.scripting.JSObject;

public class AdapterWrapFactory {
    public static Scriptable wrap(Scriptable scope, Object javaObject) {
        if (javaObject.getClass() == ScriptableMirror.class)
            return ((ScriptableMirror)javaObject).body;
        if (javaObject instanceof JSObject) {
            JSObject jsObject = (JSObject)javaObject;
            if (jsObject.isFunction()) {
                return new NativeJSObjectFunction(scope, jsObject);
            } else {
                return new NativeJSObject(scope, jsObject);
            }
        }
        return null;
    }
}
