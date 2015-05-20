/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.util;

/**
 * @author Michael Schore
 */
public class DefaultClock implements Clock {

    @Override
    public long now() {
        return System.currentTimeMillis();
    }

    @Override
    public long nowNanos() {
        return System.nanoTime();
    }
}
