/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.util;

import android.support.annotation.NonNull;

import java.util.AbstractCollection;
import java.util.ConcurrentModificationException;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * This {@link Deque} implementation has no inherent capacity restrictions and additionally
 * preserves the properties of a {@link Set}. It supports O(1) amortized performance for all
 * operations.
 *
 * The idea to use a singly-linked list (and thus save a pointer on each node) by having the
 * {@link HashMap} entry for a given element be the previous node in the list is thanks to
 * Jared Roberts.
 *
 * @author Michael Schore
 */
public class LinkedHashDeque<E> extends AbstractCollection<E> implements Deque<E>, Set<E> {

    private static final class Node<E> {
        E e;
        Node<E> next;
        Node(E e) { this.e = e; }
    }

    private Node<E> head = new Node<E>(null);
    private Node<E> tail = head;
    private HashMap<E, Node<E>> map;
    private int size = 0;
    private volatile int mutations = 0;

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
     * Removes the first {@param count} elements from the deque and re-adds
     * them to the tail.
     */
    public void rotate(int count) {
        if (size < 2) return;
        for (int i = 0; i < count; i++) {
            addLast(pollFirst());
        }
    }

    /**
     * @throws IllegalStateException if the element is already present in the deque
     */
    @Override
    public void addFirst(E e) {
        if (!offerFirst(e)) throw new IllegalStateException();
    }

    /**
     * @throws IllegalStateException if the element is already present in the deque
     */
    @Override
    public void addLast(E e) {
        if (!offerLast(e)) throw new IllegalStateException();
    }

    @Override
    public boolean offerFirst(E e) {

        if (map.containsKey(e)) return false;
        int sentinel = mutations;

        Node<E> node = new Node<E>(e);

        if (head != tail) {
            map.put(head.next.e, node);
        } else {
            tail = node;
        }

        map.put(e, head);
        node.next = head.next;
        head.next = node;

        size++;
        if (sentinel != mutations) throw new ConcurrentModificationException();
        mutations++;
        return true;
    }

    @Override
    public boolean offerLast(E e) {

        if (map.containsKey(e)) return false;
        int sentinel = mutations;

        Node<E> node = new Node<E>(e);

        tail.next = node;
        map.put(e, tail);
        tail = node;

        size++;
        if (sentinel != mutations) throw new ConcurrentModificationException();
        mutations++;
        return true;
    }

    @Override
    public E removeFirst() {
        E e = pollFirst();
        if (e == null) throw new NoSuchElementException();
        return e;
    }

    @Override
    public E removeLast() {
        E e = pollLast();
        if (e == null) throw new NoSuchElementException();
        return e;
    }

    @Override
    public E pollFirst() {
        if (size == 0) return null;
        E e = head.next.e;
        remove(e);
        return e;
    }

    @Override
    public E pollLast() {
        if (size == 0) return null;
        E e = tail.e;
        remove(e);
        return e;
    }

    @Override
    public E getFirst() {
        if (size == 0) throw new NoSuchElementException();
        return head.next.e;
    }

    @Override
    public E getLast() {
        if (size == 0) throw new NoSuchElementException();
        return tail.e;
    }

    @Override
    public E peekFirst() {
        return size > 0 ? head.next.e : null;
    }

    @Override
    public E peekLast() {
        return size > 0 ? tail.e : null;
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
        return offerLast(e);
    }

    @Override
    public void clear() {
        map.clear();
        tail = head;
        size = 0;
        mutations = 0;
    }

    @Override
    public boolean offer(E e) {
        addLast(e);
        return true;
    }

    @Override
    public E remove() {
        return removeFirst();
    }

    @Override
    public E poll() {
        return pollFirst();
    }

    @Override
    public E element() {
        return getFirst();
    }

    @Override
    public E peek() {
        return peekFirst();
    }

    @Override
    public void push(E e) {
        addFirst(e);
    }

    @Override
    public E pop() {
        return removeFirst();
    }

    @Override
    public boolean remove(Object o) {

        int sentinel = mutations;
        Node<E> node = map.remove(o);
        if (node == null) return false;

        if (node.next == tail) {
            node.next = null;
            tail = node;
        } else {
            node.next = node.next.next;
            map.put(node.next.e, node);
        }
        size--;
        if (sentinel != mutations) throw new ConcurrentModificationException();
        mutations++;
        return true;
    }

    @Override
    public boolean contains(Object o) {
        return map.containsKey(o);
    }

    @Override
    public int size() {
        return size;
    }

    @NonNull
    @Override
    public Iterator<E> iterator() {
        return new Iterator<E>() {
            Node<E> current = head;
            int sentinel = mutations;
            E removeable = null;

            @Override
            public boolean hasNext() {
                if (sentinel != mutations) throw new ConcurrentModificationException();
                return current.next != null;
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
                if (sentinel != mutations) throw new ConcurrentModificationException();
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
            int sentinel = mutations;
            E removeable = null;

            @Override
            public boolean hasNext() {
                if (sentinel != mutations) throw new ConcurrentModificationException();
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
                if (sentinel != mutations) throw new ConcurrentModificationException();
                if (removeable == null) throw new IllegalStateException();
                LinkedHashDeque.this.remove(removeable);
                sentinel++;
                removeable = null;
            }
        };
    }
}
