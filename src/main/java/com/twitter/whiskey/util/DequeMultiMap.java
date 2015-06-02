/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.util;

import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Deque;

/**
 * Abstract class for building a {@link MultiMap} with a {@link Deque} as the
 * mapped collection type.
 *
 * @author Michael Schore
 */
public abstract class DequeMultiMap<K, V> extends AbstractMultiMap<K, V, Collection<V>, Deque<V>> {

    public DequeMultiMap() {
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
    public Collection<V> wrap(Deque<V> deque) {
        return deque != null ? Collections.unmodifiableCollection(deque)
                             : Collections.<V>emptyList();
    }
}
