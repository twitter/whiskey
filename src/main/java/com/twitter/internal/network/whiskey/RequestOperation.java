package com.twitter.internal.network.whiskey;

import android.os.SystemClock;

/**
 * @author Michael Schore
 */
class RequestOperation extends CompletableFuture<Response> implements ResponseFuture {

    private final WhiskeyClient client;
    private final Request originalRequest;
    private final RequestStats stats;
    private final HeadersFutureImpl headersFuture;
    private final BodyFutureImpl bodyFuture;
    private final StatsFutureImpl statsFuture;
    private final long startMs;

    private Request currentRequest;

    RequestOperation(WhiskeyClient client, Request request) {

        startMs = SystemClock.elapsedRealtime();
        this.client = client;
        originalRequest = request;
        currentRequest = request;
        stats = new RequestStats();
        headersFuture = new HeadersFutureImpl();
        bodyFuture = new BodyFutureImpl();
        statsFuture = new StatsFutureImpl();
    }

    @Override
    boolean fail(Throwable e) {

        if (!isDone()) {
            synchronized(this) {
                if (!isDone()) {
                    finalizeStats();
                    if (!bodyFuture.isDone()) {
                        if (!headersFuture.isDone()) {
                            headersFuture.fail(e);
                        }
                        bodyFuture.fail(e);
                    }
                    statsFuture.set(stats);
                    super.fail(e);
                    return true;
                }
            }
        }

        return false;
    }

    void finalizeStats() {
        stats.durationMs = SystemClock.elapsedRealtime() - startMs;
    }

    @Override
    public Request getOriginalRequest() {
        return originalRequest;
    }

    @Override
    public Request getCurrentRequest() {
        return currentRequest;
    }

    @Override
    public HeadersFuture getHeadersFuture() {
        return headersFuture;
    }

    @Override
    public BodyFuture getBodyFuture() {
        return bodyFuture;
    }

    @Override
    public StatsFuture getStatsFuture() {
        return statsFuture;
    }
}
