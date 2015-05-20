/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.nio;

import com.twitter.whiskey.util.Clock;

import java.util.concurrent.TimeUnit;

/**
 * @author Michael Schore
 */
public class TestClock implements Clock {
    long nanoTime = 0;

    @Override
    public long now() {
        return nanoTime / 1000000;
    }

    @Override
    public long nowNanos() {
        return nanoTime;
    }

    public void tick(long nanos) {
        nanoTime += nanos;
    }

    public void tick(long duration, TimeUnit unit) {
        nanoTime += unit.toNanos(duration);
    }
}
