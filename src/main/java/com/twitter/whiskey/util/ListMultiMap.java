/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.util;

import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;

/**
 * Abstract class for building a {@link MultiMap} with a {@link List} as the
 * mapped collection type.
 *
 * @author Michael Schore
 */
public abstract class ListMultiMap<K, V> extends AbstractMultiMap<K, V, List<V>, List<V>> {

    public ListMultiMap() {
    }

    @Override
    public V getFirst(K key) {

        List<V> values = map.get(key);
        return values == null ? null : values.get(0);
    }

    @Override
    public V getLast(K key) {

        List<V> values = map.get(key);
        return values == null ? null : values.get(values.size() - 1);
    }

    @Override
    public V removeFirst(Object key) {

        int sentinel = mutations;
        List<V> values = map.get(key);
        if (values == null) return null;
        V value = values.remove(0);
        size--;
        if (values.isEmpty()) map.remove(key);
        if (sentinel != mutations++) throw new ConcurrentModificationException();
        return value;
    }

    @Override
    public Map<K, List<V>> map() {
        return Collections.unmodifiableMap(map);
    }

    @Override
    public List<V> wrap(final List<V> list) {
        return list != null ? Collections.unmodifiableList(list)
                            : Collections.<V>emptyList();
    }
}
