package com.twitter.internal.network.whiskey;

import junit.framework.TestCase;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * @author Michael Schore
 */
public class LinkedHashDequeTest extends TestCase {

    private LinkedHashDeque<Integer> deque;

    public void setUp() {
        deque = new LinkedHashDeque<>();
    }

    public void testAdd() {
        assertEquals(0, deque.size());

        assertTrue(deque.add(1));
        assertTrue(deque.add(2));
        assertTrue(deque.add(3));
        assertEquals(3, deque.size());
        assertEquals((Integer) 3, deque.getLast());
        assertEquals((Integer) 1, deque.getFirst());

        assertFalse(deque.add(1));
        assertFalse(deque.add(2));
        assertFalse(deque.add(3));
        assertEquals(3, deque.size());
    }

    public void testOfferFirst() {
        assertEquals(0, deque.size());

        assertTrue(deque.offerFirst(1));
        assertTrue(deque.offerFirst(2));
        assertTrue(deque.offerFirst(3));
        assertEquals(3, deque.size());
        assertEquals((Integer) 3, deque.getFirst());
        assertEquals((Integer) 1, deque.getLast());

        assertFalse(deque.offerFirst(1));
        assertFalse(deque.offerFirst(2));
        assertFalse(deque.offerFirst(3));
        assertEquals(3, deque.size());
    }

    public void testOfferLast() {
        assertEquals(0, deque.size());

        assertTrue(deque.offerLast(1));
        assertTrue(deque.offerLast(2));
        assertTrue(deque.offerLast(3));
        assertEquals(3, deque.size());
        assertEquals((Integer) 3, deque.getLast());
        assertEquals((Integer) 1, deque.getFirst());

        assertFalse(deque.offerLast(1));
        assertFalse(deque.offerLast(2));
        assertFalse(deque.offerLast(3));
        assertEquals(3, deque.size());
    }

    public void testAddFirst() {
        deque.addFirst(1);
        deque.addFirst(2);
        deque.addFirst(3);
        assertEquals(3, deque.size());
        assertEquals((Integer) 3, deque.getFirst());
        assertEquals((Integer) 1, deque.getLast());

        boolean exception = false;
        try {
            deque.addFirst(1);
        } catch (IllegalStateException e) {
            exception = true;
        }
        assertTrue(exception);
        assertEquals(deque.size(), 3);
    }

    public void testAddLast() {
        deque.addLast(1);
        deque.addLast(2);
        deque.addLast(3);
        assertEquals(3, deque.size());
        assertEquals((Integer) 3, deque.getLast());
        assertEquals((Integer) 1, deque.getFirst());

        boolean exception = false;
        try {
            deque.addLast(1);
        } catch (IllegalStateException e) {
            exception = true;
        }
        assertTrue(exception);
        assertEquals(3, deque.size());
    }

    public void testRemove() {
        deque.add(1);
        deque.add(2);
        deque.add(3);
        assertEquals(3, deque.size());

        assertTrue(deque.remove(1));
        assertEquals(2, deque.size());

        assertTrue(deque.remove(3));
        assertEquals(1, deque.size());
        assertEquals((Integer) 2, deque.getFirst());
        assertEquals((Integer) 2, deque.getLast());

        assertFalse(deque.remove(1));
        assertFalse(deque.remove(3));
        assertTrue(deque.remove(2));
        assertEquals(0, deque.size());
    }

    public void testRemoveFirst() {
        deque.addLast(1);
        deque.addLast(2);
        deque.addLast(3);
        assertEquals(3, deque.size());

        assertEquals((Integer) 1, deque.removeFirst());
        assertEquals((Integer) 2, deque.removeFirst());
        assertEquals((Integer) 3, deque.removeFirst());
        assertEquals(0, deque.size());

        boolean exception = false;
        try {
            deque.removeFirst();
        } catch (NoSuchElementException e) {
            exception = true;
        }
        assertTrue(exception);
        assertEquals(0, deque.size());
    }

    public void testRemoveLast() {
        deque.addFirst(1);
        deque.addFirst(2);
        deque.addFirst(3);
        assertEquals(3, deque.size());

        assertEquals((Integer) 1, deque.removeLast());
        assertEquals((Integer) 2, deque.removeLast());
        assertEquals((Integer) 3, deque.removeLast());
        assertEquals(0, deque.size());

        boolean exception = false;
        try {
            deque.removeLast();
        } catch (NoSuchElementException e) {
            exception = true;
        }
        assertTrue(exception);
    }

    public void testIterator() {
        deque.add(1);
        deque.add(2);
        deque.add(3);

        Integer expected = 1;
        for (Integer actual : deque) {
            assertEquals(expected, actual);
            expected++;
        }
        assertEquals((Integer) 4, expected);
    }

    public void testDescendingIterator() {
        deque.add(1);
        deque.add(2);
        deque.add(3);

        Integer expected = 3;
        Iterator<Integer> descending = deque.descendingIterator();
        while (descending.hasNext()) {
            assertEquals(expected, descending.next());
            expected--;
        }
        assertEquals((Integer) 0, expected);
    }
}
