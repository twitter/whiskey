package com.twitter.internal.network.whiskey;

import android.support.annotation.NonNull;

import java.util.concurrent.Executor;

/**
 * Simple executor for continued single-threaded execution.
 *
 * @author Michael Schore
 */
enum Inline implements Executor {
    INSTANCE;

    @Override
    public void execute(@NonNull Runnable command) {
        command.run();
    }

    static class Listener<T> implements com.twitter.internal.network.whiskey.Listener<T> {

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

    static class Observer<E> implements com.twitter.internal.network.whiskey.Observer<E> {

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
