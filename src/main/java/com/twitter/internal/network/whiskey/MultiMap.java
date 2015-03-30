package com.twitter.internal.network.whiskey;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * @author Michael Schore
 */
public interface MultiMap<K, V> {
    public boolean isEmpty();
    public int size();
    public V getFirst(K key);
    public V getLast(K key);
    public Collection<V> get(K key);
    public V put(K key, V value);
    public void putAll(Map<K, V> map);
    public void putAll(MultiMap<K, V> map);
    public <E extends Map.Entry<K, V>> void add(E entry);
    public <E extends Map.Entry<K, V>> void addAll(Collection<E> entries);
    public Collection<V> remove(Object key);
    public boolean removeEntry(Map.Entry<K, V> entry);
    public V removeFirst(Object key);
    void clear();
    public Set<K> keySet();
    Collection<? extends Map.Entry<K, V>> entries();
    Map<K, ? extends Collection<V>> map();
}
