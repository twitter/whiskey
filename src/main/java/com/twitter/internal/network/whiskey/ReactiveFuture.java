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

    boolean provide(final E element) throws RuntimeException {

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

    public abstract void accumulate(E element);

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
    public void addObserver(Observer<E> observer) {
        // TODO: set streaming to true

        if (!isDone()) {
            synchronized (this) {
                if (!isDone()) {
                    observers.add(observer);
                }
            }
        }
    }

    /**
     * In order to conform to the Iterator interface, this particular implementation does not
     * support interruption of a blocking call to next.
     *
     * If a call to next returns {@code null}, the consumer should treat it as an indicator of the
     * completion (via success, cancellation or error) of the Future and the end of the stream.
     */
    @Override
    public Iterator<E> iterator() {
        // TODO: set streaming to true

        return new Iterator<E>() {
            @Override
            public boolean hasNext() {
                return !isDone();
            }

            @Override
            public E next() {
                if (!isDone()) {
                    synchronized(ReactiveFuture.this) {
                        while (!isDone() && currentElement == null) {
                            try {
                                observers.wait();
                            } catch(InterruptedException e) {
                                Thread.interrupted();
                            }
                        }

                        if (error != null) {
                            throw new RuntimeException(error);
                        }

                        return currentElement;
                    }
                }

                return null;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
