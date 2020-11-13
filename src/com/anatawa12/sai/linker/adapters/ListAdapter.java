package com.anatawa12.sai.linker.adapters;

import com.anatawa12.sai.Context;
import com.anatawa12.sai.NativeArray;
import com.anatawa12.sai.ScriptRuntime;
import com.anatawa12.sai.Scriptable;
import com.anatawa12.sai.optimizer.OptRuntime;

import java.lang.reflect.Array;
import java.util.AbstractList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.RandomAccess;

public class ListAdapter extends AbstractList<Object> implements List<Object>, Deque<Object>, RandomAccess {
    private final NativeArray array;
    private final Scriptable scope;
    private final AdapterContext context;

    public ListAdapter(NativeArray array, Scriptable scope) {
        this.array = array;
        this.scope = scope;
        this.context = new AdapterContext(Context.getCurrentContext().getFactory());
    }

    public static ListAdapter create(NativeArray array) {
        return new ListAdapter(array, ScriptRuntime.getTopCallScope(Context.getCurrentContext()));
    }

    private Object call(String name, Object... argsIn) {
        return context.withContext(context -> {
            Object[] args = AdapterContext.wrapToRAll(argsIn, context);
            return AdapterContext.wrapToN(
                    OptRuntime.callN(ScriptRuntime.getPropFunctionAndThis(array, name, context, scope),
                            ScriptRuntime.lastStoredScriptable(context), args, context, scope), context);
        });
    }

    private long jsLength() {
        return array.getLength();
    }

    private Object jsGet(long index) {
        return context.withContext(context -> AdapterContext.wrapToN(array.get(index), context));
    }

    private void jsSet(long index, Object value) {
        if (index < 0 || index > jsLength()) throw invalidIndex(index);
        context.withContextV(context -> {
            if (index < Integer.MAX_VALUE) {
                array.put((int) index, array, AdapterContext.wrapToR(value, context));
            } else {
                array.put("" + index, array, AdapterContext.wrapToR(value, context));
            }
        });
    }

    private void checkNonEmpty() {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
    }

    @Override
    public void addFirst(Object o) {
        offerFirst(o);
    }

    @Override
    public void addLast(Object o) {
        offerLast(o);
    }

    @Override
    public boolean offerFirst(Object o) {
        call("unshift", o);
        return true;
    }

    @Override
    public boolean offerLast(Object o) {
        call("push", o);
        return true;
    }

    @Override
    public Object removeFirst() {
        checkNonEmpty();
        return pollFirst();
    }

    @Override
    public Object removeLast() {
        checkNonEmpty();
        return pollLast();
    }

    @Override
    public Object pollFirst() {
        return call("shift");
    }

    @Override
    public Object pollLast() {
        return call("pop");
    }

    @Override
    public Object getFirst() {
        checkNonEmpty();
        return peekFirst();
    }

    @Override
    public Object getLast() {
        checkNonEmpty();
        return peekLast();
    }

    @Override
    public Object peekFirst() {
        return isEmpty() ? null : jsGet(0);
    }

    @Override
    public Object peekLast() {
        return isEmpty() ? null : jsGet(jsLength() - 1);
    }

    @Override
    public boolean removeFirstOccurrence(Object o) {
        Iterator<?> it = iterator();
        while (it.hasNext()) {
            if (Objects.equals(o, it.next())) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean removeLastOccurrence(Object o) {
        Iterator<?> it = descendingIterator();
        while (it.hasNext()) {
            if (Objects.equals(o, it.next())) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean offer(Object o) {
        return offerLast(o);
    }

    @Override
    public Object remove() {
        return removeFirst();
    }

    @Override
    public Object poll() {
        return pollFirst();
    }

    @Override
    public Object element() {
        return getFirst();
    }

    @Override
    public Object peek() {
        return peekFirst();
    }

    @Override
    public void push(Object o) {
        addFirst(o);
    }

    @Override
    public Object pop() {
        return removeFirst();
    }

    @Override
    public Iterator<Object> descendingIterator() {
        return new Iterator<Object>() {
            private final ListIterator<Object> itr = listIterator(jsLength());

            @Override
            public boolean hasNext() {
                return itr.hasPrevious();
            }

            @Override
            public Object next() {
                return itr.previous();
            }

            @Override
            public void remove() {
                itr.remove();
            }
        };
    }

    @Override
    public int size() {
        long longLen = jsLength();
        if (longLen > Integer.MAX_VALUE) {
            throw new IllegalStateException("too long");
        }
        return (int) longLen;
    }

    @Override
    public boolean isEmpty() {
        return jsLength() == 0;
    }

    @Override
    public Iterator<Object> iterator() {
        return listIterator();
    }

    private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];

    @Override
    public Object[] toArray() {
        return toArray(EMPTY_OBJECT_ARRAY);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T[] toArray(T[] a) {
        int size = size();
        if (size <= a.length) {
            int i = 0;
            for (; i < size; i++) {
                a[i] = (T)get(i);
            }
            for (; i < a.length; i++) {
                a[i] = null;
            }
        } else {
            a = (T[])Array.newInstance(a.getClass().getComponentType(), size);
            for (int i = 0; i < size; i++) {
                a[i] = (T)get(i);
            }
        }
        return a;
    }

    @Override
    public boolean add(Object o) {
        return offer(o);
    }

    @Override
    public boolean remove(Object o) {
        return removeFirstOccurrence(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object o : c)
            if (!contains(o))
                return false;
        return true;
    }

    @Override
    public boolean addAll(Collection<?> c) {
        boolean modified = false;
        for (Object o : c) {
            add(o);
            modified = true;
        }
        return modified;
    }

    @Override
    public boolean addAll(int index, Collection<?> c) {
        if (index < 0 || index > jsLength()) throw invalidIndex(index);
        boolean modified = false;
        for (Object e : c) {
            add(index++, e);
            modified = true;
        }
        return modified;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        Objects.requireNonNull(c);
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
    public boolean retainAll(Collection<?> c) {
        Objects.requireNonNull(c);
        boolean modified = false;
        Iterator<Object> it = iterator();
        while (it.hasNext()) {
            if (!c.contains(it.next())) {
                it.remove();
                modified = true;
            }
        }
        return modified;
    }

    @Override
    public void clear() {
        Iterator<Object> it = this.iterator();
        while (it.hasNext()) {
            it.next();
            it.remove();
        }
    }

    @Override
    public Object get(int index) {
        return jsGet(index);
    }

    @Override
    public Object set(int index, Object element) {
        Object result = get(index);
        jsSet(index, element);
        return result;
    }

    @Override
    public void add(int index, Object element) {
        add((long)index, element);
    }

    public void add(long index, Object element) {
        if(index < 0) {
            throw invalidIndex(index);
        } else if(index == 0) {
            addFirst(element);
        } else {
            final int size = size();
            if(index < size) {
                call("splice", index, 0, element);
            } else if(index == size) {
                addLast(element);
            } else {
                throw invalidIndex(index);
            }
        }
    }

    private IndexOutOfBoundsException invalidIndex(long index) {
        return new IndexOutOfBoundsException("index: " + index + ", length: " + jsLength());
    }

    @Override
    public Object remove(int index) {
        return removeAt(index);
    }

    public Object removeAt(long index) {
        final Object prevValue = jsGet(index);
        call("splice", index, 1);
        return prevValue;
    }

    @Override
    public int indexOf(Object o) {
        ListIterator<Object> it = listIterator();
        if (o==null) {
            while (it.hasNext())
                if (it.next()==null)
                    return it.previousIndex();
        } else {
            while (it.hasNext())
                if (o.equals(it.next()))
                    return it.previousIndex();
        }
        return -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        ListIterator<Object> it = listIterator(size());
        if (o==null) {
            while (it.hasPrevious())
                if (it.previous()==null)
                    return it.nextIndex();
        } else {
            while (it.hasPrevious())
                if (o.equals(it.previous()))
                    return it.nextIndex();
        }
        return -1;
    }

    @Override
    public ListIterator<Object> listIterator() {
        return listIterator(0L);
    }

    @Override
    public ListIterator<Object> listIterator(int index) {
        return listIterator((long)index);
    }

    public ListIterator<Object> listIterator(long index) {
        return new ListIterator<Object>() {
            private long cur = index;
            private long lastRet = -1;

            @Override
            public boolean hasNext() {
                return 0 <= cur && cur < jsLength();
            }

            @Override
            public Object next() {
                try {
                    if (!hasNext()) throw new NoSuchElementException();
                    return jsGet(lastRet = cur++);

                } catch (IndexOutOfBoundsException ex) {
                    throw new ConcurrentModificationException();
                }
            }

            @Override
            public boolean hasPrevious() {
                return 0 < cur && cur <= jsLength();
            }

            @Override
            public Object previous() {
            try {
                if (!hasPrevious()) throw new NoSuchElementException();
                return jsGet(lastRet = --cur);

                } catch (IndexOutOfBoundsException ex) {
                    throw new ConcurrentModificationException();
                }
            }

            @Override
            public int nextIndex() {
                if (cur > Integer.MAX_VALUE) {
                    throw new IllegalStateException("too long");
                }
                return (int)cur;
            }

            @Override
            public int previousIndex() {
                if (cur - 1 > Integer.MAX_VALUE) {
                    throw new IllegalStateException("too long");
                }
                return (int) cur - 1;
            }

            @Override
            public void remove() {
                if (lastRet < 0)
                    throw new IllegalStateException();

                try {
                    ListAdapter.this.removeAt(lastRet);
                    cur = lastRet;
                    lastRet = -1;
                } catch (IndexOutOfBoundsException ex) {
                    throw new ConcurrentModificationException();
                }
            }

            public void set(Object e) {
                if (lastRet < 0)
                    throw new IllegalStateException();

                try {
                    jsSet(lastRet, e);
                } catch (IndexOutOfBoundsException ex) {
                    throw new ConcurrentModificationException();
                }
            }

            public void add(Object e) {
                try {
                    ListAdapter.this.add(cur++, e);
                    lastRet = -1;
                } catch (IndexOutOfBoundsException ex) {
                    throw new ConcurrentModificationException();
                }
            }
        };
    }
}
