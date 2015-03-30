package com.twitter.internal.network.whiskey;

import android.support.annotation.NonNull;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * @author Michael Schore
 */
public abstract class AbstractMultiMap<K, V> implements MultiMap<K, V> {
    protected Map<K, Deque<V>> map;
    protected volatile int mutations = 0;
    protected int size = 0;

    public AbstractMultiMap() {
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public Collection<V> get(K key) {

        Deque<V> values = map.get(key);
        return values == null ? Collections.<V>emptySet()
                              : Collections.unmodifiableCollection(values);
    }

    @Override
    public V getFirst(K key) {

        Deque<V> values = map.get(key);
        return values == null ? null : values.peekFirst();
    }

    @Override
    public V getLast(K key) {

        Deque<V> values = map.get(key);
        return values == null ? null : values.peekLast();
    }

    @Override
    public V put(K key, V value) {

        int sentinel = mutations;
        Deque<V> values = map.get(key);
        if (values == null) {
            values = constructDeque(4);
            map.put(key, values);
        }
        values.addLast(value);
        size++;
        if (sentinel != mutations++) throw new ConcurrentModificationException();
        return value;
    }

    @Override
    public void putAll(Map<K, V> map) {
        for (Map.Entry<K, V> entry : map.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void putAll(MultiMap<K, V> map) {
        for (Map.Entry<K, V> entry : map.entries()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public <E extends Map.Entry<K, V>> void add(E entry) {
        put(entry.getKey(), entry.getValue());
    }

    @Override
    public <E extends Map.Entry<K, V>> void addAll(Collection<E> entries) {
        for (E entry : entries) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public Collection<V> remove(Object key) {

        int sentinel = mutations;
        Collection<V> values = map.remove(key);
        if (values == null) return Collections.emptySet();
        size -= values.size();
        if (values.isEmpty()) map.remove(key);
        if (sentinel != mutations++) throw new ConcurrentModificationException();
        return Collections.unmodifiableCollection(values);
    }

    @Override
    public boolean removeEntry(Map.Entry<K, V> entry) {

        int sentinel = mutations;
        Deque<V> values = map.get(entry.getKey());
        if (!values.remove(entry.getValue())) return false;
        size--;
        if (sentinel != mutations++) throw new ConcurrentModificationException();
        return true;
    }

    @Override
    public V removeFirst(Object key) {

        int sentinel = mutations;
        Deque<V> values = map.get(key);
        if (values == null) return null;
        V value = values.pollFirst();
        size--;
        if (values.isEmpty()) map.remove(key);
        if (sentinel != mutations++) throw new ConcurrentModificationException();
        return value;
    }

    @Override
    public void clear() {
        map.clear();
        size = 0;
        mutations = 0;
    }

    @Override
    public Set<K> keySet() {
        // TODO: consider returning a set that supports remove (MS)
        return Collections.unmodifiableSet(map.keySet());
    }

    @Override
    public Map<K, ? extends Collection<V>> map() {
        return Collections.unmodifiableMap(map);
    }

    protected Deque<V> getDeque(K key) {
        return map.get(key);
    }

    protected abstract Deque<V> constructDeque(int initialCapacity);

    protected class DefaultEntryCollection extends EntryCollection<Map.Entry<K, V>> {

        @Override
        protected Map.Entry<K, V> constructEntry(K key, V value) {
            return new AbstractMap.SimpleImmutableEntry<>(key, value);
        }
    }

    protected abstract class EntryCollection<E extends Map.Entry<K, V>> extends AbstractCollection<E> {

        protected abstract E constructEntry(K key, V value);

        @Override
        public int size() {
            return size;
        }

        @Override
        public boolean isEmpty() {
            return size == 0;
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean contains(Object o) {

            if (!(o instanceof Map.Entry)) return false;
            E entry = (E) o;
            Deque<V> values = map.get(entry.getKey());
            return values != null && values.contains(entry.getValue());
        }

        @Override
        public boolean add(E entry) {

            put(entry.getKey(), entry.getValue());
            return true;
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean remove(Object o) {
            return o instanceof Map.Entry && removeEntry((Map.Entry<K, V>) o);
        }

        @Override
        public void clear() {
            AbstractMultiMap.this.clear();
        }

        @Override
        @NonNull
        public Iterator<E> iterator() {
            return new Iterator<E>() {
                Iterator<Map.Entry<K, Deque<V>>> dequeIterator = map.entrySet().iterator();
                Iterator<V> currentIterator;
                K currentKey;
                E removeable;
                int sentinel = mutations;

                @Override
                public boolean hasNext() {

                    if (currentIterator == null || !currentIterator.hasNext()) {
                        if (dequeIterator.hasNext()) {
                            Map.Entry<K, Deque<V>> entry = dequeIterator.next();
                            currentIterator = entry.getValue().iterator();
                            currentKey = entry.getKey();
                        } else {
                            return false;
                        }
                    }

                    // Because we remove empty deques, we know that a newly-set currentIterator
                    // must contain at least one value.
                    return true;
                }

                @Override
                public E next() {

                    if (!hasNext()) throw new NoSuchElementException();
                    removeable = constructEntry(currentKey, currentIterator.next());
                    if (sentinel != mutations) throw new ConcurrentModificationException();
                    return removeable;
                }

                @Override
                public void remove() {

                    if (removeable == null) throw new IllegalStateException();
                    removeEntry(removeable);
                    removeable = null;
                    if (++sentinel != mutations) throw new ConcurrentModificationException();
                }
            };
        }
    }
}
