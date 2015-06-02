/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.util;

import android.support.annotation.NonNull;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Abstract class providing most of the functionality necessary for
 * implementing a {@link MultiMap}.
 *
 * @author Michael Schore
 * @param <K> the key type for the multi-map
 * @param <V> the value type for the multi-map
 * @param <C> the public collection type for methods that return groups of values
 * @param <G> the internal collection type used by the multi-map
 */
public abstract class AbstractMultiMap<K, V, C extends Collection<V>, G extends C> implements MultiMap<K, V> {
    protected Map<K, G> map;
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
    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    @Override
    public C get(K key) {
        return wrap(map.get(key));
    }

    @Override
    public V getFirst(K key) {

        G values = map.get(key);
        return values == null ? null : values.iterator().next();
    }

    @Override
    public V getLast(K key) {

        G values = map.get(key);
        if (values == null) return null;
        V value = null;
        for (V v : values) value = v;
        return value;
    }

    @Override
    public void put(K key, V value) {

        int sentinel = mutations;
        G values = map.get(key);
        if (values == null) {
            values = newCollection(4);
            map.put(key, values);
        }
        values.add(value);
        size++;
        if (sentinel != mutations++) throw new ConcurrentModificationException();
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
    public C remove(Object key) {

        int sentinel = mutations;
        G values = map.remove(key);
        if (values == null) return wrap(newCollection(0));
        size -= values.size();
        if (values.isEmpty()) map.remove(key);
        if (sentinel != mutations++) throw new ConcurrentModificationException();
        return wrap(values);
    }

    @Override
    public boolean removeEntry(Map.Entry<K, V> entry) {

        int sentinel = mutations;
        C values = map.get(entry.getKey());
        if (!values.remove(entry.getValue())) return false;
        size--;
        if (sentinel != mutations++) throw new ConcurrentModificationException();
        return true;
    }

    @Override
    public V removeFirst(Object key) {

        int sentinel = mutations;
        C values = map.get(key);
        if (values == null) return null;
        Iterator<V> i = values.iterator();
        V value = i.next();
        i.remove();
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

    // TODO: this implementation unfortunately provides no protection against
    // modifications to collections contained in this map. We can guard it or
    // consider making the collections update the internal size and mutations
    // instance variables.
    @Override
    public Map<K, ? extends C> map() {
        return Collections.unmodifiableMap(map);
    }

    /**
     * Used to wrap values from methods that return collections, e.g.
     * to make them immutable if desired.
     */
    protected abstract C wrap(G collection);

    /**
     * Used to instatiate a new collection for the underlying {@link Map}
     */
    protected abstract G newCollection(int initialCapacity);

    protected class DefaultEntryCollection extends EntryCollection<Map.Entry<K, V>> {

        @Override
        protected Map.Entry<K, V> newEntry(K key, V value) {
            return new AbstractMap.SimpleImmutableEntry<>(key, value);
        }
    }

    /**
     * Since entries may be a concrete subtype, this internal class allows a
     * returned entry collection to also adopt that subtype.
     */
    protected abstract class EntryCollection<E extends Map.Entry<K, V>> extends AbstractCollection<E> {

        protected abstract E newEntry(K key, V value);

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
            G values = map.get(entry.getKey());
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
                Iterator<Map.Entry<K, G>> entryIterator = map.entrySet().iterator();
                Iterator<V> valueIterator;
                K currentKey;
                E removeable;
                int sentinel = mutations;

                @Override
                public boolean hasNext() {

                    if (valueIterator == null || !valueIterator.hasNext()) {
                        if (entryIterator.hasNext()) {
                            Map.Entry<K, G> entry = entryIterator.next();
                            valueIterator = entry.getValue().iterator();
                            currentKey = entry.getKey();
                        } else {
                            return false;
                        }
                    }

                    // Because we remove empty collections, we know that valueIterator
                    // must contain at least one value.
                    return true;
                }

                @Override
                public E next() {

                    if (!hasNext()) throw new NoSuchElementException();
                    removeable = newEntry(currentKey, valueIterator.next());
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
