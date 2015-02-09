package com.twitter.internal.network.whiskey;

import android.support.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Michael Schore
 */
class InlineExecutor implements Executor {

    private static AtomicBoolean once = new AtomicBoolean(false);
    private static InlineExecutor instance;

    static InlineExecutor instance() {

        if (once.compareAndSet(false, true)) {
            instance = new InlineExecutor();
        }
        return instance;
    }

    @Override
    public void execute(@NonNull Runnable command) {
        command.run();
    }
}
