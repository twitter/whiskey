package com.twitter.whiskey.futures;

import android.support.annotation.NonNull;

import java.util.concurrent.Executor;

/**
 * Simple executor for continued single-threaded execution.
 *
 * @author Michael Schore
 */
public enum Inline implements Executor {
    INSTANCE;

    @Override
    public void execute(@NonNull Runnable command) {
        command.run();
    }

    public static class Listener<T> implements com.twitter.whiskey.futures.Listener<T> {

        @Override
        public void onComplete(T result) {
        }

        @Override
        public void onError(Throwable throwable) {
        }

        @Override
        public Executor getExecutor() {
            return INSTANCE;
        }
    }

    public static class Observer<E> implements com.twitter.whiskey.futures.Observer<E> {

        @Override
        public void onComplete() {
        }

        @Override
        public void onNext(E element) {
        }

        @Override
        public void onError(Throwable throwable) {
        }

        @Override
        public Executor getExecutor() {
            return INSTANCE;
        }
    }
}
