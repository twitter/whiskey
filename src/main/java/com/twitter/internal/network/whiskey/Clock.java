package com.twitter.internal.network.whiskey;

/**
 * Clock is a simple interface for retrieving relative timestamps. The intent
 * is to abstract away platform and implementation-specific differences and
 * allow for testability of timed events without relying on non-deterministic
 * waits and timeouts in tests.
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
