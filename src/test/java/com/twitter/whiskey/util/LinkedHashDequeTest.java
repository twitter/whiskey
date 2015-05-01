package com.twitter.whiskey.util;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * @author Michael Schore
 */
public class LinkedHashDequeTest {

    private LinkedHashDeque<Integer> deque;

    @Before
    public void setUp() {
        deque = new LinkedHashDeque<>();
    }

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
    public void testIteratorRemove() {
        deque.add(1);
        deque.add(2);
        deque.add(3);

        Iterator<Integer> i = deque.iterator();
        int expectedValue = 1;
        int expectedSize = 3;
        while (i.hasNext()) {
            assertEquals(expectedSize--, deque.size());
            assertEquals(expectedValue++, i.next().intValue());
            i.remove();
        }
        assertEquals(0, expectedSize);
        assertTrue(deque.isEmpty());

        IllegalStateException error = null;
        try {
            i.remove();
        } catch (IllegalStateException e) {
            error = e;
        }
        assertNotNull(error);
    }

    @Test
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

    @Test
    public void testDescendingIteratorRemove() {
        deque.add(1);
        deque.add(2);
        deque.add(3);

        Iterator<Integer> i = deque.descendingIterator();
        int expectedValue = 3;
        int expectedSize = 3;
        while (i.hasNext()) {
            assertEquals(expectedSize--, deque.size());
            assertEquals(expectedValue--, i.next().intValue());
            i.remove();
        }
        assertEquals(0, expectedSize);
        assertTrue(deque.isEmpty());

        IllegalStateException error = null;
        try {
            i.remove();
        } catch (IllegalStateException e) {
            error = e;
        }
        assertNotNull(error);
    }
}
