/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.net;

import com.twitter.whiskey.futures.CompletableFuture;
import com.twitter.whiskey.futures.Observer;
import com.twitter.whiskey.util.Platform;

import java.util.Iterator;
import java.util.concurrent.ExecutionException;

/**
 * Internal object tracking the complete lifecycle of an HTTP request.  May
 * span multiple redirects and/or attempts. Also functions as the internal
 * implementation of the {@link ResponseFuture} used to provide feedback
 * and return results to the application.
 *
 * @author Michael Schore
 */
class RequestOperation extends CompletableFuture<Response> implements ResponseFuture {

    private final WhiskeyClient client;
    private final Request originalRequest;
    private final RequestStats stats;
    private final HeadersFutureImpl headersFuture;
    private final BodyFutureImpl bodyFuture;
    private final StatsFutureImpl statsFuture;
    private final PushFuture pushFuture;
    private final long startMs;

    private Request currentRequest;
    private int remainingRedirects;
    private int remainingRetries;

    RequestOperation(WhiskeyClient client, Request request) {

        startMs = Platform.instance().timestamp();

        this.client = client;
        originalRequest = request;
        currentRequest = request;
        remainingRedirects = request.getMaxRedirects();
        remainingRetries = 1;
        stats = new RequestStats();

        headersFuture = new HeadersFutureImpl();
        bodyFuture = new BodyFutureImpl();
        statsFuture = new StatsFutureImpl();
        pushFuture = new PushFuture();
    }

    void redirect(Request request) {

        currentRequest = request;
        remainingRedirects--;
        assert remainingRedirects >= 0 && !headersFuture.isDone()
            && !bodyFuture.isDone() && !statsFuture.isDone();
        headersFuture.reset();
        client.queue(this);
    }

    void retry() {

        remainingRetries--;
        assert remainingRetries >= 0 && !headersFuture.isDone()
            && !bodyFuture.isDone() && !statsFuture.isDone();
        client.queue(this);
    }

    @Override
    public boolean fail(Throwable e) {

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

    void complete(int statusCode) {

        if (isDone()) throw new RuntimeException("operation already completed");
        finalizeStats();
        headersFuture.complete();
        bodyFuture.complete();
        pushFuture.complete();
        statsFuture.set(stats);
        try {
            set(new Response(statusCode, headersFuture.get(), bodyFuture.get(), stats));
        } catch (ExecutionException | InterruptedException e) {
            fail(e);
        }
    }

    void finalizeStats() {
        stats.durationMs = Platform.instance().timestamp() - startMs;
    }

    int getRemainingRedirects() {
        return remainingRedirects;
    }

    int getRemainingRetries() {
        return remainingRetries;
    }

    WhiskeyClient getClient() {
        return client;
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

    PushFuture getPushFuture() {
        return pushFuture;
    }

    @Override
    public void addPushObserver(Observer<ResponseFuture> observer) {
        pushFuture.addObserver(observer);
    }

    @Override
    public Iterator<ResponseFuture> pushIterator() {
        return pushFuture.iterator();
    }
}
