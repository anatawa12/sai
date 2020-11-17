package com.anatawa12.sai.linker.adapters;

import com.anatawa12.sai.Callable;
import com.anatawa12.sai.Context;
import com.anatawa12.sai.Function;
import com.anatawa12.sai.ScriptRuntime;
import com.anatawa12.sai.Scriptable;
import jdk.nashorn.api.scripting.AbstractJSObject;
import jdk.nashorn.api.scripting.JSObject;

import javax.script.Bindings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// TODO: Test
public class ScriptableMirror extends AbstractJSObject implements JSObject, Map<String, Object>, Bindings {
    private final Scriptable body;
    private final AdapterContext context;

    static Scriptable scope(Context context) {
        return ScriptRuntime.getTopCallScope(context);
    }

    private String k(Object key) {
        String str = (String) key;
        if (str.isEmpty()) throw new IllegalArgumentException("key is empty");
        return str;
    }

    // region JSObject

    @Override
    public Object call(Object p0, Object... p1) {
        return context.withContext(context -> AdapterContext.wrapToN(((Callable) body).call(
                context,
                scope(context),
                AdapterContext.wrapNewObjectToR(p0, context),
                AdapterContext.wrapToRAll(p1, context)
        ), context));
    }

    @Override
    public Object newObject(Object... p0) {
        return context.withContext(context -> AdapterContext.wrapToN(((Function) body).construct(context, scope(context), AdapterContext.wrapToRAll(p0, context)), context));
    }

    @Override
    public Object eval(String p0) {
        return context.withContext(context -> AdapterContext.wrapToN(ScriptRuntime.evalSpecial(
                context,
                scope(context),
                body,
                new Object[0],
                "<eval>",
                -1), context));
    }

    @Override
    public Object getMember(String p0) {
        return context.withContext(context -> AdapterContext.wrapToN(body.get(p0, body), context));
    }

    @Override
    public Object getSlot(int p0) {
        return context.withContext(context -> AdapterContext.wrapToN(body.get(p0, body), context));
    }

    @Override
    public boolean hasMember(String p0) {
        return context.withContext(context -> body.has(p0, body));
    }

    @Override
    public boolean hasSlot(int p0) {
        return context.withContext(context -> body.has(p0, body));
    }

    @Override
    public void removeMember(String p0) {
        context.withContextV(context -> body.delete(p0));
    }

    @Override
    public void setMember(String p0, Object p1) {
        context.withContextV(context -> body.put(p0, body, p1));
    }

    @Override
    public void setSlot(int p0, Object p1) {
        context.withContextV(context -> body.put(p0, body, p1));
    }

    private final KeySet keys = new KeySet();

    @Override
    public Set<String> keySet() {
        return keys;
    }

    private final Collection<Object> values = new ValueCollection();

    @Override
    public Collection<Object> values() {
        return values;
    }

    @Override
    public boolean isInstance(Object p0) {
        return context.withContext(context -> body.hasInstance(AdapterContext.wrapNewObjectToR(p0, context)));
    }

    @Override
    public boolean isInstanceOf(Object p0) {
        return context.withContext(context -> {
            Scriptable wrapped = AdapterContext.wrapNewObjectToR(p0, context);
            return wrapped != null && wrapped.hasInstance(body);
        });
    }

    @Override
    public String getClassName() {
        return body.getClassName();
    }

    @Override
    public boolean isFunction() {
        return body instanceof Function;
    }

    @Override
    public boolean isStrictFunction() {
        return isFunction();
    }

    @Override
    public boolean isArray() {
        return false;
    }

    @SuppressWarnings("deprecation")
    @Override
    public double toNumber() {
        return ScriptRuntime.toNumber(body.getDefaultValue(Number.class));
    }

    public Object getDefaultValue(Class<?> hint) {
        return body.getDefaultValue(Number.class);
    }

    //endregion

    // region Map

    @Override
    public int size() {
        return keys.size();
    }

    @Override
    public boolean isEmpty() {
        return keys.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return keys.contains(k(key));// TODO
    }

    @Override
    public boolean containsValue(Object value) {
        return false;// TODO
    }

    @Override
    public Object get(Object key) {
        return context.withContext(context -> AdapterContext.wrapToN(ScriptRuntime.getObjectElem(body, k(key), context), context));
    }

    @Override
    public Object put(String key, Object value) {
        Object result = get(key);
        context.withContext(context -> AdapterContext.wrapToN(ScriptRuntime.setObjectElem(body, k(key), value, context), context));
        return result;
    }

    @Override
    public Object remove(Object key) {
        if (key instanceof String) {
            Object result = get(key);
            context.withContext(context -> AdapterContext.wrapToN(ScriptRuntime.deleteObjectElem(body, k(key), context), context));
            return result;
        }
        return null;
    }

    @Override
    public void putAll(Map<? extends String, ?> m) {
        for (Entry<? extends String, ?> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clear() {
        entrySet().clear();
    }

    private EntrySet entrySet = new EntrySet();

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return entrySet;
    }

    // endregion

    private class KeySet implements Set<String> {
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Stream<String> stream() {
            return (Stream<String>) (Stream) Arrays.stream(body.getIds()).filter(id -> id instanceof String);
        }

        @Override
        public int size() {
            //noinspection ReplaceInefficientStreamCount
            return (int) stream().count();
        }

        @Override
        public boolean isEmpty() {
            return !stream().findFirst().isPresent();
        }

        @Override
        public boolean contains(Object o) {
            return stream().anyMatch(key -> key.equals(o));
        }

        @Override
        public Iterator<String> iterator() {
            return new Iterator<String>() {
                private final Iterator<String> itr = stream().iterator();
                private String lastReturn;

                @Override
                public void remove() {
                    if (lastReturn == null) throw new IllegalStateException();
                    ScriptableMirror.this.remove(lastReturn);
                    lastReturn = null;
                }

                @Override
                public boolean hasNext() {
                    return itr.hasNext();
                }

                @Override
                public String next() {
                    return lastReturn = itr.next();
                }
            };
        }

        @Override
        public Object[] toArray() {
            //noinspection SimplifyStreamApiCallChains
            return stream().toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            //noinspection SuspiciousToArrayCall
            return new ArrayList<>(this).toArray(a);
        }

        @Override
        public boolean add(String s) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object o) {
            boolean result = contains(o);
            if (result) ScriptableMirror.this.remove(o);
            return result;
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return c.stream().allMatch(this::contains);
        }

        @Override
        public boolean addAll(Collection<? extends String> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            Objects.requireNonNull(c);
            boolean modified = false;
            Iterator<String> it = iterator();
            while (it.hasNext()) {
                if (!c.contains(it.next())) {
                    it.remove();
                    modified = true;
                }
            }
            return modified;
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            boolean modified = false;
            Iterator<?> it = iterator();
            while (it.hasNext()) {
                if (c.contains(it.next())) {
                    it.remove();
                    modified = true;
                }
            }
            return modified;
        }

        @Override
        public void clear() {
            for (String s : this) {
                ScriptableMirror.this.remove(s);
            }
        }
    }

    private class ValueCollection implements Collection<Object> {
        @Override
        public int size() {
            return keys.size();
        }

        @Override
        public boolean isEmpty() {
            return keys.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            for (Object o1 : this) {
                if (Objects.equals(o1, o)) return true;
            }
            return false;
        }

        @Override
        public Iterator<Object> iterator() {
            return new Iterator<Object>() {
                private final Iterator<String> iterator = keys.iterator();

                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public Object next() {
                    return get(iterator.next());
                }

                @Override
                public void remove() {
                    iterator.remove();
                }
            };
        }

        @Override
        public Object[] toArray() {
            return keys.stream().map(ScriptableMirror.this::get).toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            //noinspection SuspiciousToArrayCall
            return keys.stream().map(ScriptableMirror.this::get).collect(Collectors.toList()).toArray(a);
        }

        @Override
        public boolean add(Object s) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object o) {
            for (Iterator<String> iterator = keys.iterator(); iterator.hasNext(); ) {
                String key = iterator.next();
                if (get(key).equals(o)) {
                    iterator.remove();
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return c.stream().allMatch(this::contains);
        }

        @Override
        public boolean addAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            boolean modified = false;
            for (Iterator<?> it = iterator(); it.hasNext(); ) {
                if (c.contains(it.next())) {
                    it.remove();
                    modified = true;
                }
            }
            return modified;
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            Objects.requireNonNull(c);
            boolean modified = false;
            for (Iterator<?> it = iterator(); it.hasNext(); ) {
                if (!c.contains(it.next())) {
                    it.remove();
                    modified = true;
                }
            }
            return modified;
        }

        @Override
        public void clear() {
            keys.clear();
        }
    }

    private class EntrySet implements Set<Entry<String, Object>> {
        @Override
        public int size() {
            return keys.size();
        }

        @Override
        public boolean isEmpty() {
            return keys.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            return keys.contains(e.getKey()) && Objects.equals(get(e.getKey()), e.getValue());
        }

        @Override
        public Iterator<Entry<String, Object>> iterator() {
            return new Iterator<Entry<String, Object>>() {
                final Iterator<String> iterator = keys.iterator();

                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public Entry<String, Object> next() {
                    return new TheEntry(iterator.next());
                }

                @Override
                public void remove() {
                    iterator.remove();
                }
            };
        }

        @Override
        public Object[] toArray() {
            //noinspection SimplifyStreamApiCallChains
            return stream().toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            //noinspection SuspiciousToArrayCall
            return new ArrayList<>(this).toArray(a);
        }

        @Override
        public boolean remove(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
            if (!Objects.equals(get(entry.getKey()), entry)) {
                return false;
            }
            ScriptableMirror.this.remove(entry.getKey());
            return true;
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return c.stream().allMatch(this::contains);
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            boolean modified = false;
            for (Iterator<?> it = iterator(); it.hasNext(); ) {
                if (!c.contains(it.next())) {
                    it.remove();
                    modified = true;
                }
            }
            return modified;
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            boolean modified = false;
            for (Iterator<?> it = iterator(); it.hasNext(); ) {
                if (c.contains(it.next())) {
                    it.remove();
                    modified = true;
                }
            }
            return modified;
        }

        @Override
        public void clear() {
            keys.clear();
        }

        @Override
        public boolean addAll(Collection<? extends Entry<String, Object>> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean add(Entry<String, Object> stringObjectEntrySet) {
            throw new UnsupportedOperationException();
        }
    }

    private class TheEntry implements Entry<String, Object> {
        private final String key;

        public TheEntry(String key) {
            this.key = key;
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public Object getValue() {
            return get(key);
        }

        @Override
        public Object setValue(Object value) {
            return put(key, value);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Entry<?, ?>)) return false;
            Entry<?, ?> that = (Entry<?, ?>) o;
            return Objects.equals(this.getKey(), that.getKey())
                    && Objects.equals(this.getValue(), that.getValue());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(getKey()) ^ Objects.hashCode(getValue());
        }
    }

    ScriptableMirror(Scriptable body, Context context) {
        super();
        this.body = body;
        this.context = new AdapterContext(context.getFactory());
    }

    // internal
    public static ScriptableMirror create(Scriptable body, Context context) {
        return new ScriptableMirror(body, context);
    }
}
