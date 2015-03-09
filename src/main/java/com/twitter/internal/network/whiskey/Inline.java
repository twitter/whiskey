package com.twitter.internal.network.whiskey;

import android.support.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Michael Schore
 */
enum Inline implements Executor {
    INSTANCE;

    @Override
    public void execute(@NonNull Runnable command) {
        command.run();
    }
}
