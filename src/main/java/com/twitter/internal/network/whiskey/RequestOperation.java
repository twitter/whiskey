package com.twitter.internal.network.whiskey;

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
    private int remainingRedirects;
    private int remainingRetries;

    RequestOperation(WhiskeyClient client, Request request) {

        startMs = PlatformAdapter.get().timestamp();

        this.client = client;
        originalRequest = request;
        currentRequest = request;
        remainingRedirects = request.getMaxRedirects();
        remainingRetries = 1;
        stats = new RequestStats();

        headersFuture = new HeadersFutureImpl();
        bodyFuture = new BodyFutureImpl();
        statsFuture = new StatsFutureImpl();
    }

    void redirect(Request request) {

        currentRequest = request;
        remainingRedirects--;
        assert remainingRedirects > 0;
        assert !headersFuture.isDone();
        assert !bodyFuture.isDone();
        assert !statsFuture.isDone();
        client.queue(this);
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
        stats.durationMs = PlatformAdapter.get().timestamp() - startMs;
    }

    public int getRemainingRedirects() {
        return remainingRedirects;
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
    public HeadersFutureImpl getHeadersFuture() {
        return headersFuture;
    }

    @Override
    public BodyFutureImpl getBodyFuture() {
        return bodyFuture;
    }

    @Override
    public StatsFutureImpl getStatsFuture() {
        return statsFuture;
    }
}
