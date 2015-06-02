/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.net;

/**
 * Simple struct for providing metrics related to the handling of a
 * {@link Request}.
 *
 * @author Michael Schore
 */
public class RequestStats {

    public Protocol protocol;
    public long durationMs;
    public long queuedMs;
    public long blockedMs;
    public long latencyMs;
    public long serviceMs;
    public long rxBytes;
    public long txBytes;
    public int attempts;
    public int redirects;
    public int streamId;

    RequestStats() {
    }
}
