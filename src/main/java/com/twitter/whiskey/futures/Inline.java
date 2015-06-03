/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.futures;

import java.util.concurrent.Executor;

/**
 * Simple executor for continued single-threaded execution.
 *
 * WARNING: Do not use this executor to register listeners or observers on
 * Whiskey's public network APIs! This is unsupported behavior, will result
 * in application code being run on the network thread, and may be explicitly
 * prevented by guards in the future.
 *
 * @author Michael Schore
 */
public enum Inline implements Executor {
    INSTANCE;

    @Override
    public void execute(Runnable command) {
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
