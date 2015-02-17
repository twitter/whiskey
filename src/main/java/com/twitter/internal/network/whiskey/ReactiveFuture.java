package com.twitter.internal.network.whiskey;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;

public abstract class ReactiveFuture<T, E> extends CompletableFuture<T> implements ObservableFuture<T, E> {

    private final ArrayList<Observer<E>> observers = new ArrayList<>();
    private volatile Throwable error = null;
    private volatile E currentElement = null;
    private volatile boolean streaming = false;

    ReactiveFuture() {
    }

    boolean provide(final E element, final boolean last) throws RuntimeException {

        if (isDone()) {
            if (isCancelled()) {
                return false;
            } else {
                throw new RuntimeException("progress cannot be updated once future is fulfilled");
            }
        }

        synchronized(this) {
            if (isDone()) {
                if (isCancelled()) {
                    return false;
                } else {
                    throw new RuntimeException("progress cannot be updated once future is fulfilled");
                }
            }

            if (!streaming) {
                accumulate(element);
                if (last) complete();
                return true;
            }

            for (final Observer<E> observer : observers) {
                observer.getExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        observer.onNext(element);
                    }
                });
            }

            currentElement = element;
            observers.notifyAll();
            currentElement = null;

            return true;
        }
    }

    abstract void accumulate(E element);
    abstract Iterable<E> drain();
    abstract void complete();

    @Override
    boolean fail(final Throwable throwable) {

        if (super.fail(throwable)) {
            synchronized(this) {
                for (final Observer<E> observer : observers) {
                    observer.getExecutor().execute(new Runnable() {
                        @Override
                        public void run() {
                            observer.onError(throwable);
                        }
                    });
                }

                error = throwable;
                observers.notifyAll();
                return true;
            }
        }

        return false;
    }

    @Override
    public void addObserver(final Observer<E> observer) {

        if (!streaming) {
            synchronized (this) {
                if (!streaming) {

                    streaming = true;
                    observers.add(observer);
                    for (final E element : drain()) {
                        observer.getExecutor().execute(new Runnable() {
                            @Override
                            public void run() {
                                observer.onNext(element);
                            }
                        });
                    }
                    return;
                }
            }
        }

        throw new UnsupportedOperationException("streaming is transient and supports only one consumer");
    }

    /**
     * In order to conform to the Iterator interface, this implementation propagates
     * {@link InterruptedException} on a blocking call to next by wrapping it in a
     * {@link RuntimeException}.
     *
     * If a call to next returns {@code null}, the consumer should treat it as an indicator of the
     * completion (via success, cancellation or error) of the Future and the end of the stream.
     */
    @Override
    public Iterator<E> iterator() {

        if (!streaming) {
            synchronized (this) {
                if (!streaming) {

                    streaming = true;

                }
            }
        }
        return new StreamingIterator(drain().iterator());
    }

    private class StreamingIterator implements Iterator<E> {
        private Iterator<E> drained;

        StreamingIterator(Iterator<E> drained) {
            this.drained = drained;
        }

        @Override
        public boolean hasNext() {
            return drained.hasNext() || !isDone();
        }

        @Override
        public E next() {
            if (drained.hasNext()) {
                return drained.next();
            } else if (!isDone()) {
                synchronized(ReactiveFuture.this) {
                    while (!isDone() && currentElement == null) {
                        try {
                            observers.wait();
                        } catch(InterruptedException e) {
                            Thread.interrupted();
                            throw new RuntimeException(e);
                        }
                    }

                    if (error != null) {
                        throw new RuntimeException(error);
                    }

                    return currentElement;
                }
            }

            throw new NoSuchElementException();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
