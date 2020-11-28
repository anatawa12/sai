package com.anatawa12.sai.linker.adapters;

import com.anatawa12.sai.Context;
import com.anatawa12.sai.NativeJavaObject;
import com.anatawa12.sai.Scriptable;
import jdk.nashorn.api.scripting.JSObject;

import java.util.Objects;

public class NativeJSObject extends NativeJavaObject {
    NativeJSObject(Scriptable scope, JSObject javaObject) {
        super(scope, Objects.requireNonNull(javaObject, "javaObject"), null);
    }

    protected JSObject getBody() {
        return (JSObject) unwrap();
    }

    public String getClassName() {
        return getBody().getClassName();
    }

    public Object get(String name, Scriptable start) {
        return AdapterContext.wrapToR(getBody().getMember(name), parent, Context.getCurrentContext());
    }

    public Object get(int index, Scriptable start) {
        return AdapterContext.wrapToR(getBody().getSlot(index), parent, Context.getCurrentContext());
    }

    public boolean has(String name, Scriptable start) {
        return getBody().hasMember(name);
    }

    public boolean has(int index, Scriptable start) {
        return getBody().hasSlot(index);
    }

    public void put(String name, Scriptable start, Object value) {
        getBody().setMember(name, AdapterContext.wrapToN(value, Context.getCurrentContext()));
    }

    public void put(int index, Scriptable start, Object value) {
        getBody().setSlot(index, AdapterContext.wrapToN(value, Context.getCurrentContext()));
    }

    public void delete(String name) {
        getBody().removeMember(name);
    }

    public void delete(int index) {
        delete(String.valueOf(index));
    }

    public Object[] getIds() {
        return getBody().keySet().toArray(new Object[0]);
    }

    public boolean hasInstance(Scriptable instance) {
        return getBody().isInstance(instance);
    }
}
