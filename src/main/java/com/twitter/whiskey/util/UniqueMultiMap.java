/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.util;

import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link MultiMap} that maintains the property that every value stored in the map is unique.
 * Note this is the exact inverse of the {@link Map} property where every key is unique, allowing
 * any UniqueMultiMap to be inverted into a Map and vice versa.
 *
 * @author Michael Schore
 */
public class UniqueMultiMap<K, V> extends DequeMultiMap<K, V> {
    private Map<V, K> inverse = new HashMap<>();
    private DefaultEntryCollection entries = new DefaultEntryCollection();

    public UniqueMultiMap() {
        map = new HashMap<>();
    }

    public UniqueMultiMap(Map<V, K> inverse) {
        map = new HashMap<>();
        this.inverse = new HashMap<>(inverse);
        for (Map.Entry<V, K> inverseEntry : inverse.entrySet()) {
            super.put(inverseEntry.getValue(), inverseEntry.getKey());
        }
    }

    @Override
    public void put(K key, V value) {

        if (inverse.containsKey(value)) removeValue(value);
        super.put(key, value);
        inverse.put(value, key);
    }

    @Override
    public boolean removeEntry(Map.Entry<K, V> entry) {
        return removeValue(entry.getValue()) != null;
    }

    @Override
    public V removeFirst(Object key) {

        V value = super.removeFirst(key);
        if (value != null) inverse.remove(value);
        return value;
    }

    @Override
    public Collection<? extends Map.Entry<K, V>> entries() {
        return entries;
    }

    public K removeValue(V value) {

        int sentinel = mutations;
        K key = inverse.remove(value);
        if (key == null) return null;

        Deque<V> values = map.get(key);
        if (!values.remove(value)) throw new IllegalStateException();
        size--;
        if (values.isEmpty()) map.remove(key);
        if (sentinel != mutations++) throw new ConcurrentModificationException();
        return key;
    }

    public Map<V, K> inverse() {
        return inverse;
    }

    @Override
    protected Deque<V> newCollection(int initialCapacity) {
        return new LinkedHashDeque<>(initialCapacity);
    }
}
