package com.twitter.internal.network.whiskey;

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
