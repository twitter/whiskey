/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.futures;

import com.twitter.whiskey.util.Clock;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lock-free implementation of an ObservableFuture.
 *
 * @author Michael Schore
 */
public abstract class ReactiveLocklessFuture<T, E> extends LocklessFuture<T> implements ObservableFuture<T, E> {

    private static final Object SENTINEL = new Object();
    private final ConcurrentLinkedQueue<Observer<E>> observers = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<StreamingIterator> iterators = new ConcurrentLinkedQueue<>();
    private volatile Throwable error = null;
    private volatile boolean drained = false;
    private volatile boolean streaming = false;

    private static final int OBSERVED = 1 << 31;
    private static final int RELEASED = 1 << 30;
    private static final int DRAINING = 1 << 29;
    private static final int STREAMED = 1 << 28;

    private final AtomicInteger gate = new AtomicInteger(0);

    public ReactiveLocklessFuture(Clock clock) {
        super(clock);
    }

    public boolean provide(final E element) throws RuntimeException {

        if (isDone()) return false;

        // If we're not yet streaming, start counting active concurrent
        // calls to provide, addObserver and release. If and when the future
        // is both observed and released, the last call to finish will be
        // responsible for starting streaming.
        int local = gate.incrementAndGet();
        if ((gate.incrementAndGet() & STREAMED) == 0) {
            accumulate(element);
            // If the lower-order bits of gate are 0 (i.e. this may be
            // the last concurrent call), attempt the compareAndSet to
            // start streaming.
            if (gate.decrementAndGet() == (OBSERVED | RELEASED) &&
                gate.compareAndSet((OBSERVED | RELEASED), STREAMED)) {
                stream();
            }
        } else {
            // Streaming has already started, so simply dispatch.
            dispatch(element);
            gate.decrementAndGet();
        }

        return true;
    }

    /**
     * Called by the future's creator to begin streaming to iterators and observers.
     */
    public void release() {

        if (isDone()) return;

        for(;;) {
            int local = gate.get();
            if ((local & STREAMED) != 0) return;
            if (gate.compareAndSet(local, ((local + 1) & RELEASED))) break;
        }

        if (gate.decrementAndGet() == (OBSERVED | RELEASED) &&
            gate.compareAndSet((OBSERVED | RELEASED), STREAMED)) {
            stream();
        }
    }

    /**
     * Called by the future's creator to complete the future.
     */
    public boolean finish() {

        if (isDone()) return false;

        // Streaming hasn't already started
        if ((gate.incrementAndGet() & STREAMED) == 0) {
            boolean result = complete();

            // It's too late to avoid complete accumulation, but we'll still allow
            // the first registered observer to playback the stream.
            if (gate.decrementAndGet() == (OBSERVED | RELEASED) &&
                gate.compareAndSet((OBSERVED | RELEASED), STREAMED)) {
                stream();
            }

            return result;
        }

        boolean result = set(null);
        if (result) {
            for (final Observer observer : observers) {
                observer.getExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        observer.onComplete();
                    }
                });
            }

            for (final StreamingIterator iterator : iterators) {
                iterator.queue(SENTINEL);
            }
        }
        return result;
    }

    @Override
    public boolean fail(final Throwable throwable) {

        if ((gate.incrementAndGet() & STREAMED) == 0) {
            boolean result = super.fail(throwable);

            if (gate.decrementAndGet() == (OBSERVED | RELEASED) &&
                gate.compareAndSet((OBSERVED | RELEASED), STREAMED)) {
                stream();
            }

            return result;
        }

        if (super.fail(throwable)) {
            error = throwable;

            for (final Observer<E> observer : observers) {
                observer.getExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        observer.onError(throwable);
                    }
                });
            }

            for (final StreamingIterator iterator : iterators) {
                iterator.queue(SENTINEL);
            }

            return true;
        }

        return false;
    }

    protected abstract void accumulate(E element);
    protected abstract Iterable<E> drain();
    protected abstract boolean complete();

    private void dispatch(final E element) {

        for (final Observer<E> observer : observers) {
            observer.getExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    observer.onNext(element);
                }
            });
        }

        for (final StreamingIterator iterator : iterators) {
            iterator.queue(element);
        }
    }

    @Override
    public void addObserver(final Observer<E> observer) {

        int local;
        do {
            local = gate.get();
            if ((local & STREAMED) != 0) {
                observers.add(observer);
                return;
            }
        } while (!gate.compareAndSet(local, ((local + 1) & OBSERVED)));

        observers.add(observer);
        if (gate.decrementAndGet() == (OBSERVED | RELEASED) &&
            gate.compareAndSet((OBSERVED | RELEASED), STREAMED)) {
            stream();
        }
    }

    private void stream() {

        Iterable<E> drained = drain();
        for (final E element : drained) {
            for (final Observer<E> observer : observers) {
                observer.getExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        observer.onNext(element);
                    }
                });
            }
        }

        if (isDone()) {
            if (error != null) {
                for (final Observer<E> observer : observers) {
                    observer.getExecutor().execute(new Runnable() {
                        @Override
                        public void run() {
                            observer.onError(error);
                        }
                    });
                }
            } else if (isCancelled()) {
                for (final Observer<E> observer : observers) {
                    observer.getExecutor().execute(new Runnable() {
                        @Override
                        public void run() {
                            observer.onError(new CancellationException());
                        }
                    });
                }
            } else {
                for (final Observer<E> observer : observers) {
                    observer.getExecutor().execute(new Runnable() {
                        @Override
                        public void run() {
                            observer.onComplete();
                        }
                    });
                }
            }
        }

        for (final StreamingIterator iterator : iterators) {
            iterator.setDrained(drained.iterator());
            if (isDone()) iterator.queue(SENTINEL);
        }
    }

    /**
     * In order to conform to the Iterator interface, this implementation propagates
     * {@link InterruptedException} on blocking calls by wrapping them in a
     * {@link RuntimeException}.
     */
    @Override
    public Iterator<E> iterator() {

        StreamingIterator i;
        if (streaming && !drained) {
            synchronized (this) {
                if (streaming && !drained) {
                    drained = true;
                    i = new StreamingIterator();
                    iterators.add(i);
                    return i;
                }
            }
        }

        i = new StreamingIterator();
        iterators.add(i);
        return i;
    }

    private class StreamingIterator implements Iterator<E> {
        private BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
        private Iterator<E> drained;
        Object currentElement;

        StreamingIterator() {
        }

        void setDrained(Iterator<E> drained) {
            this.drained = drained;
        }

        void queue(Object o) {
            queue.add(o);
        }

        @Override
        public boolean hasNext() {

            if ((drained != null && drained.hasNext()) || (currentElement != null && currentElement != SENTINEL)) {
                return true;
            }

            if (isDone() && queue.isEmpty()) return false;

            synchronized(ReactiveLocklessFuture.this) {
                if (isDone() && queue.isEmpty()) return false;
            }

            try {
                currentElement = queue.take();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            if (currentElement != SENTINEL) return true;
            if (error != null) throw new RuntimeException(error);

            return false;
        }

        @SuppressWarnings("unchecked")
        @Override
        public E next() {

            if (drained.hasNext()) return drained.next();

            if (currentElement != null && currentElement != SENTINEL) {
                E element = (E) currentElement;
                currentElement = null;
                return element;
            }

            if (isDone() && queue.isEmpty()) throw new NoSuchElementException();

            synchronized (ReactiveLocklessFuture.this) {
                if (isDone() && queue.isEmpty()) throw new NoSuchElementException();
            }

            if (!hasNext()) throw new NoSuchElementException();
            E element = (E) currentElement;
            currentElement = null;
            return element;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
