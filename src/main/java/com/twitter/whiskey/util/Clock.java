/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.util;

/**
 * Clock is a simple interface for retrieving relative timestamps. The intent
 * is to abstract away platform and implementation-specific differences and
 * allow for testability of timed events without relying on non-deterministic
 * scheduling of waits and timeouts.
 *
 * @author Michael Schore
 */
public interface Clock {

    /**
     * Returns a timestamp in milliseconds that's guaranteed to be monotically
     * increasing. No correlation to "real world" calendar date or time is
     * guaranteed or implied.
     *
     * @return the current time in milliseconds
     */
    public long now();

    /**
     * Returns a timestamp in nanoseconds that's guaranteed to be monotically
     * increasing. No correlation to "real world" calendar date or time is
     * guaranteed or implied.
     *
     * @return the current time in nanoseconds
     */
    public long nowNanos();
}
