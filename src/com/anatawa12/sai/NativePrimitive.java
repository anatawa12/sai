package com.anatawa12.sai;

public abstract class NativePrimitive extends IdScriptableObject {
    @Override
    public Object get(String name, Scriptable start) {
        Object value = super.get(name, start);
        if (value != NOT_FOUND) return value;
        if (!hasPrototypeMap()) return NOT_FOUND;
        Context cx = Context.getCurrentContext();
        if (cx != null && cx.hasFeature(Context.FEATURE_NATIVE_PRIMITIVES_HAVE_JAVA_METHODS)) {
            JavaMembers members = getJavaMembers();
            value = members.get(this, name, unwrap(), false);
            if (value != NOT_FOUND)
                return value;
        }
        return NOT_FOUND;
    }

    private transient JavaMembers members;

    protected JavaMembers initJavaMembers() {
        return members = JavaMembers.lookupClass(getParentScope(), unwrappedType(), null, false);
    }

    /**
     * The type of unwrapped type. The type should be one of Boolean, Double or String
     * and the type must be final class.
     *
     * @return unwrapped value type.
     */
    private JavaMembers getJavaMembers() {
        if (members != null) return members;
        return initJavaMembers();
    }

    /**
     * Unwrap the object by returning the wrapped value.
     *
     * @return a wrapped value
     */
    public abstract Object unwrap();

    /**
     * The type of unwrapped type. The type should be one of Boolean, Double or String
     * and the type must be final class.
     *
     * @return unwrapped value type.
     */
    public abstract Class<?> unwrappedType();
}
