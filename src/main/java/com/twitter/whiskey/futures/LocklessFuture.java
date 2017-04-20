/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.futures;

import com.twitter.whiskey.util.Clock;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * Lock-free implementation of a ListenableFuture.
 *
 * @author Michael Schore
 */
public class LocklessFuture<T> implements ListenableFuture<T> {

    protected static final int INIT = 0;
    protected static final int STARTED = 0x0000000f;
    protected static final int FINISHING = 19;
    protected static final int DONE = 0x0000ffff;

    protected static final int SUCCESS = 1 << 31;
    protected static final int CANCELLED = 1 << 30;
    protected static final int ERROR = 1 << 29;

    private static final int FLAG_MASK  = 0xffff0000;
    private static final int STATE_MASK = 0x0000ffff;

    private final AtomicInteger state = new AtomicInteger(INIT);
    private final ConcurrentLinkedQueue<Listener<T>> listeners = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Thread> waiters = new ConcurrentLinkedQueue<>();
    private final Clock clock;

    private volatile T result = null;
    private volatile Throwable error = null;
    private volatile boolean block = true;
    private volatile boolean notify = false;

    public LocklessFuture(Clock clock) {
        this.clock = clock;
    }

    public boolean set(final T result) {

        if (!atomicSetState(SUCCESS | DONE)) return false;
        this.result = result;
        block = false;
        Thread waiter;
        while ((waiter = waiters.poll()) != null) {
            LockSupport.unpark(waiter);
        }

        Listener<T> listener;
        while ((listener = listeners.poll()) != null) {
            final Listener<T> fListener = listener;
            fListener.getExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    fListener.onComplete(result);
                }
            });
        }

        notify = true;
        return true;
    }

    public boolean fail(final Throwable error) {

        if (!atomicSetState(ERROR | DONE)) return false;
        this.error = error;
        block = false;
        Thread waiter;
        while ((waiter = waiters.poll()) != null) {
            LockSupport.unpark(waiter);
        }

        Listener<T> listener;
        while ((listener = listeners.poll()) != null) {
            final Listener<T> fListener = listener;
            fListener.getExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    fListener.onError(error);
                }
            });
        }

        notify = true;
        return true;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {

        if (!atomicSetState(CANCELLED | DONE)) return false;
        block = false;
        Thread waiter;
        while ((waiter = waiters.poll()) != null) {
            LockSupport.unpark(waiter);
        }

        Listener<T> listener;
        while ((listener = listeners.poll()) != null) {
            final Listener<T> fListener = listener;
            fListener.getExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    fListener.onError(new CancellationException());
                }
            });
        }

        notify = true;
        return true;
    }

    @Override
    public boolean isCancelled() {
        return (state.get() & CANCELLED) != 0;
    }

    @Override
    public boolean isDone() {
        return (state.get() & STATE_MASK) >= DONE;
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {

        if (block) {
            Thread currentThread = Thread.currentThread();
            waiters.add(currentThread);
            while (block) {
                LockSupport.park(this);
                if (Thread.interrupted()) throw new InterruptedException();
            }
        }

        assert((state.get() & STATE_MASK) == DONE);

        if (isCancelled()) {
            throw new CancellationException();
        }

        if (error != null) {
            throw new ExecutionException(error);
        }

        return result;
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {

        long deadline = clock.nowNanos() + unit.toNanos(timeout);
        if (block) {
            Thread currentThread = Thread.currentThread();
            waiters.add(currentThread);
            while (block) {
                if (clock.nowNanos() >= deadline) throw new TimeoutException();
                LockSupport.parkNanos(this, deadline);
                if (Thread.interrupted()) throw new InterruptedException();
            }
        }

        assert((state.get() & STATE_MASK) == DONE);

        if (isCancelled()) {
            throw new CancellationException();
        }

        if (error != null) {
            throw new ExecutionException(error);
        }

        return result;
    }

    @Override
    public void addListener(final Listener<T> listener) {

        if (block) {
            listeners.add(listener);
            if (block) return;
            if (notify && !listeners.contains(listener)) return;
        }

        if (isCancelled()) {
            listener.getExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    listener.onError(new CancellationException());
                }
            });
        }

        if (error != null) {
            listener.getExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    listener.onError(error);
                }
            });
        }

        listener.getExecutor().execute(new Runnable() {
            @Override
            public void run() {
                listener.onComplete(result);
            }
        });
    }

    final protected boolean atomicSetFlag(int flag) {
        final int oldState = state.get();
        return (oldState & flag) == 0 && state.compareAndSet(oldState, oldState & flag);
    }

    final protected boolean atomicSetState(int newState) {
        final int oldState = state.get();
        return (newState & STATE_MASK) > (oldState & STATE_MASK) &&
            state.compareAndSet(oldState, newState);
    }
}
