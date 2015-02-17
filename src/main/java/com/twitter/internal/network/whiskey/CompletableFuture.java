package com.twitter.internal.network.whiskey;

import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class CompletableFuture<T> implements ListenableFuture<T> {

    private ArrayList<Listener<T>> listeners = new ArrayList<>();
    private volatile T result = null;
    private volatile Throwable error = null;
    private volatile boolean cancelled = false;
    volatile boolean done = false;

    CompletableFuture() {
    }

    boolean set(final T result) throws RuntimeException {

        if (done) {
            if (cancelled) {
                return false;
            } else {
                throw new RuntimeException("cannot be set after completion");
            }
        }

        synchronized(this) {
            if (done) {
                if (cancelled) {
                    return false;
                } else {
                    throw new RuntimeException("cannot be set after completion");
                }
            }

            this.result = result;
            done = true;

            for (final Listener<T> listener : listeners) {
                listener.getExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        listener.onComplete(result);
                    }
                });
            }

            notifyAll();
            return true;
        }
    }

    boolean fail(final Throwable throwable) {

        if (done) {
            if (cancelled) {
                return false;
            } else {
                throw new RuntimeException("cannot be failed after completion");
            }
        }

        synchronized(this) {
            if (done) {
                if (cancelled) {
                    return false;
                } else {
                    throw new RuntimeException("cannot be failed after completion");
                }
            }

            error = throwable;
            done = true;

            for (final Listener<T> listener : listeners) {
                listener.getExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        listener.onError(error);
                    }
                });
            }

            notifyAll();
            return true;
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {

        if (done) {
            return false;
        }

        synchronized(this) {
            if (done) {
                return false;
            }

            cancelled = true;
            done = true;

            final Exception e = new CancellationException();
            for (final Listener<T> listener : listeners) {
                listener.getExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        listener.onError(e);
                    }
                });
            }

            notifyAll();
            return true;
        }
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        if (!done) {
            synchronized(this) {
                if (!done) {
                    wait();
                }
            }
        }

        if (error != null) {
            throw new ExecutionException(error);
        }

        if (cancelled) {
            throw new CancellationException();
        }

        return result;
    }

    @Override
    public T get(long timeout, @NonNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (!done) {
            synchronized(this) {
                if (!done) {
                    wait(unit.toMillis(timeout));
                }
            }
        }

        if (!done) {
            throw new TimeoutException();
        }

        if (error != null) {
            throw new ExecutionException(error);
        }

        if (cancelled) {
            throw new CancellationException();
        }

        return result;
    }

    @Override
    public void addListener(final Listener<T> listener) {

        boolean notify = false;
        if (!done) {
            synchronized (this) {
                if (!done) {
                    listeners.add(listener);
                } else {
                    notify = true;
                }
            }
        } else {
            notify = true;
        }

        if (notify) {
            if (result != null) {
                listener.getExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        listener.onComplete(result);
                    }
                });
            } else if (error != null) {
                listener.getExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        listener.onError(error);
                    }
                });
            } else {
                listener.getExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        listener.onError(new CancellationException());
                    }
                });
            }
        }
    }
}
