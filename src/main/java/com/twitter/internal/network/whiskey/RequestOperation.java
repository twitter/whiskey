package com.twitter.internal.network.whiskey;

/**
 * @author Michael Schore
 */
class RequestOperation {
    private final Request originalRequest;
    private final WhiskeyClient client;
    private Request currentRequest;

    RequestOperation(Request request, WhiskeyClient client) {
        this.client = client;
        originalRequest = request;
        currentRequest = request;
        startTimeout();
    }

    private void startTimeout() {

    }

    public Request getOriginalRequest() {
        return originalRequest;
    }

    public Request getCurrentRequest() {
        return currentRequest;
    }

    ResponseFuture getFuture() {
        return null;
    }
}
