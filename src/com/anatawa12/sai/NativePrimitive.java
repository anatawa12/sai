package com.anatawa12.sai;

public abstract class NativePrimitive extends IdScriptableObject {
    @Override
    public Object get(String name, Scriptable start) {
        Object value = super.get(name, start);
        if (value != NOT_FOUND) return value;
        if (!hasPrototypeMap()) return NOT_FOUND;
        JavaMembers members = getJavaMembers();
        if (members != null) {
            value = members.get(this, name, unwrap(), false);
            if (value != NOT_FOUND)
                return value;
        }
        return NOT_FOUND;
    }

    /**
     * The type of unwrapped type. The type should be one of Boolean, Double or String
     * and the type must be final class.
     *
     * @return unwrapped value type.
     */
    protected abstract JavaMembers getJavaMembers();

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
    public final Class<?> unwrappedType() {
        return getJavaMembers().getCl();
    }
}
