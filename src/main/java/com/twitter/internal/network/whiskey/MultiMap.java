package com.twitter.internal.network.whiskey;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * A data structure that maps keys to zero or more values. MultiMap supports
 * an interface very close to {@link Map}, but necessarily deviates from it
 * in a few respects to support required operations.
 *
 * The design is intended to allow for a great deal of flexibility and
 * interchangeability for managing mappings within {@link Collection}s,
 * {@link Map}s, and implementations of this interface. In particular,
 * implementations may define their own {@link Map.Entry} subtype and
 * use it directly, overriding the defined method signatures.
 *
 * @author Michael Schore
 */
public interface MultiMap<K, V> {

    /**
     * @return true if this map contains no key-value mappings
     */
    public boolean isEmpty();

    /**
     * Returns the number of key-value mappings in this map. Note this
     * may be greater than the number of keys in the map.
     *
     * @return the number of key-value mappings in this map
     */
    public int size();

    /**
     * @return true if this map contains a mapping for the specified
     */
    boolean containsKey(Object key);

    /**
     * Returns the first value to which the specified key is mapped. Note
     * this may or may not maintain insertion-order depending on the
     * implementation.
     *
     * @return the first value to which the specified key is mapped, or
     *         {@code null} if this map contains no mapping for the key
     */
    public V getFirst(K key);

    /**
     * Returns the last value to which the specified key is mapped. Note
     * this may or may not maintain insertion-order depending on the
     * implementation.
     *
     * @return the last value to which the specified key is mapped, or
     *         {@code null} if this map contains no mapping for the key
     */
    public V getLast(K key);

    /**
     * Returns a collection of all values to which the specified key is
     * mapped.
     *
     * @return a collection of values to which the specified key is mapped
     *         or an empty collection if no such mappings are present
     */
    public Collection<V> get(K key);

    /**
     * Creates a mapping from the specified key to the specified value.
     */
    public void put(K key, V value);

    /**
     * Copies all mappings from the specified map to this one.
     */
    public void putAll(Map<K, V> map);

    /**
     * Copies all mappings from the specified MultiMap to this one.
     */
    public void putAll(MultiMap<K, V> map);

    /**
     * Adds the specified mapping to this map.
     */
    public <E extends Map.Entry<K, V>> void add(E entry);

    /**
     * Adds all mappings in the specified collection to this map.
     */
    public <E extends Map.Entry<K, V>> void addAll(Collection<E> entries);

    /**
     * Removes all mappings associated with the specified key.
     *
     * @return a collection of values to which the key had been mapped
     *         or an empty collection if no such mappings were present
     */
    public Collection<V> remove(Object key);

    /**
     * Removes the specified mapping from this map, if it exists.
     *
     * @return true if the mapping was present and removed
     */
    public boolean removeEntry(Map.Entry<K, V> entry);

    /**
     * Removes the first value to which the specified key is mapped.
     *
     * @return the value that was removed or {@code null} if no mappings
     *         were present
     */
    public V removeFirst(Object key);

    /**
     * Removes all key-value mappings from this map.
     */
    void clear();

    /**
     * @return the set of unique keys for which mappings exist in this map
     */
    public Set<K> keySet();

    /**
     * Returns an optionally mutable collection of all key-value collections
     * in this map. If the returned collection is mutable, changes to the
     * collection will affect the map and vice-versa.
     *
     * @return a collection of all key-value mappings contained in this map
     */
    Collection<? extends Map.Entry<K, V>> entries();

    /**
     * Returns an optionally mutable view of this map as a {@link Map} of
     * keys to collections of values. If the returned map is mutable,
     * changes to it will be reflected in this map and vice-versa.
     *
     * @return a view of the map as a {@link Map} of keys to collections
     *         of values
     */
    Map<K, ? extends Collection<V>> map();
}
