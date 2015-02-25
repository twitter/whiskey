package com.twitter.internal.network.whiskey;

import android.support.annotation.NonNull;

import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * This {@link Deque} implementation has no inherent capacity restrictions and additionally
 * preserves the properties of a {@link Set}. It supports O(1) amortized performance for all
 * operations. It is a close analogue to {@link java.util.LinkedHashSet}, but with greater
 * accessibility and flexibility.
 *
 * @author Michael Schore
 */
public class LinkedHashDeque<E> implements Deque<E>, Set<E> {

    static final class Node<E> {
        E e;
        Node<E> next;
        Node(E e) { this.e = e; }
    }

    private Node<E> head = new Node<E>(null);
    private Node<E> tail = head;
    private HashMap<E, Node<E>> map;
    private int size = 0;
    private volatile int permutations = 0;

    public LinkedHashDeque() {
        map = new HashMap<>();
    }

    public LinkedHashDeque(int capacity) {
        map = new HashMap<>(capacity);
    }

    public LinkedHashDeque(int capacity, float loadFactor) {
        map = new HashMap<>(capacity, loadFactor);
    }

    /**
     * Removes the first {@param count} elements from the Deque and re-adds
     * them to the tail.
     */
    public void rotate(int count) {
        if (size < 2) return;
        for (int i = 0; i < count; i++) {
            addLast(getFirst());
        }
    }

    /**
     * If the element is already present in the Deque, it is removed and
     * added to the head, preserving the {@link Set} property.
     */
    @Override
    public void addFirst(E e) {

        int sentinel = permutations;

        Node<E> node = map.get(e);
        if (node != null && head.next != node) {
            remove(e);
        } else {
            node = new Node<E>(e);
        }

        if (head != tail) {
            map.put(head.next.e, node);
        } else {
            tail = node;
        }

        map.put(e, head);
        node.next = head.next;
        head.next = node;

        if (sentinel != permutations) throw new ConcurrentModificationException();
        permutations++;
    }

    /**
     * If the element is already present in the Deque, it is removed and
     * added to the tail, preserving the {@link Set} property.
     */
    @Override
    public void addLast(E e) {

        int sentinel = permutations;

        Node<E> node = map.get(e);
        if (node != null && tail != node) {
            remove(e);
        } else {
            node = new Node<E>(e);
        }

        tail.next = node;
        map.put(e, tail);
        tail = node;

        if (sentinel != permutations) throw new ConcurrentModificationException();
        permutations++;
    }

    @Override
    public boolean offerFirst(E e) {
        addFirst(e);
        return true;
    }

    @Override
    public boolean offerLast(E e) {
        addLast(e);
        return true;
    }

    @Override
    public E removeFirst() {
        if (size == 0) throw new NoSuchElementException();
        E e = head.next.e;
        remove(e);
        return e;
    }

    @Override
    public E removeLast() {
        return null;
    }

    @Override
    public E pollFirst() {
        return null;
    }

    @Override
    public E pollLast() {
        return null;
    }

    @Override
    public E getFirst() {
        return null;
    }

    @Override
    public E getLast() {
        return null;
    }

    @Override
    public E peekFirst() {
        return null;
    }

    @Override
    public E peekLast() {
        return null;
    }

    @Override
    public boolean removeFirstOccurrence(Object o) {
        return remove(o);
    }

    @Override
    public boolean removeLastOccurrence(Object o) {
        return remove(o);
    }

    @Override
    public boolean add(E e) {
        if (e == tail.e) return false;
        addLast(e);
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends E> collection) {
        boolean permuted = false;
        for (E e : collection) {
            if (add(e)) permuted = true;
        }
        return permuted;
    }

    @Override
    public void clear() {
        map.clear();
        tail = head;
        size = 0;
    }

    @Override
    public boolean offer(E e) {
        return false;
    }

    @Override
    public E remove() {
        return null;
    }

    @Override
    public E poll() {
        return null;
    }

    @Override
    public E element() {
        return null;
    }

    @Override
    public E peek() {
        return null;
    }

    @Override
    public void push(E e) {

    }

    @Override
    public E pop() {
        return null;
    }

    @Override
    public boolean remove(Object o) {
        return false;
    }

    @Override
    public boolean removeAll(Collection<?> collection) {
        return false;
    }

    @Override
    public boolean retainAll(Collection<?> collection) {
        return false;
    }

    @Override
    public boolean contains(Object o) {
        return map.get(o) != null;
    }

    @Override
    public boolean containsAll(Collection<?> collection) {
        boolean present = true;
        for (Object o : collection) {
            if (map.get(o) == null) present = false;
        }
        return present;
    }

    @Override
    public boolean isEmpty() {
        // return head == tail;
        return size == 0;
    }

    @Override
    public int size() {
        return size;
    }

    @NonNull
    @Override
    public Object[] toArray() {
        Object[] arr = new Object[size];
        Node<E> current = head;
        for (int i = 0; i < size; i++) {
            current = current.next;
            arr[i] = current.e;
        }
        return arr;
    }

    @NonNull
    @Override
    public <T> T[] toArray(T[] array) {

        return null;
    }

    @Override
    public Iterator<E> iterator() {
        return new Iterator<E>() {
            Node<E> current = head;
            int sentinel = permutations;
            E removeable = null;

            @Override
            public boolean hasNext() {
                if (sentinel != permutations) throw new ConcurrentModificationException();
                return current != tail;
            }

            @Override
            public E next() {
                if (!hasNext()) throw new NoSuchElementException();
                current = current.next;
                removeable = current.e;
                return removeable;
            }

            @Override
            public void remove() {
                if (sentinel != permutations) throw new ConcurrentModificationException();
                if (removeable == null) throw new IllegalStateException();
                LinkedHashDeque.this.remove(removeable);
                sentinel++;
                removeable = null;
            }
        };
    }

    @NonNull
    @Override
    public Iterator<E> descendingIterator() {
        return new Iterator<E>() {
            Node<E> current = tail;
            int sentinel = permutations;
            E removeable = null;

            @Override
            public boolean hasNext() {
                if (sentinel != permutations) throw new ConcurrentModificationException();
                return current != head;
            }

            @Override
            public E next() {
                if (!hasNext()) throw new NoSuchElementException();
                removeable = current.e;
                current = map.get(removeable);
                return removeable;
            }

            @Override
            public void remove() {
                if (sentinel != permutations) throw new ConcurrentModificationException();
                if (removeable == null) throw new IllegalStateException();
                LinkedHashDeque.this.remove(removeable);
                sentinel++;
                removeable = null;
            }
        };
    }
}
