package com.twitter.internal.network.whiskey;

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
