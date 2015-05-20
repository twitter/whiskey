/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.net;

import com.twitter.whiskey.nio.RunLoop;
import com.twitter.whiskey.util.Origin;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Michael Schore
 */
public class WhiskeyClient {
    final private ClientConfiguration configuration;
    final private HashMap<Origin, SessionManager> managers = new HashMap<>();
    final private ConcurrentHashMap<Origin, Origin> aliases = new ConcurrentHashMap<>();

    public WhiskeyClient() {
        this(new ClientConfiguration.Builder().create());
    }

    public WhiskeyClient(ClientConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Submits a request for execution. A new {@link RunLoop} will be started
     * if necessary, and new connections established where required.
     *
     * @param request the request to be executed
     * @return a ResponseFuture tracking progress of the submitted request
     */
    public ResponseFuture submit(Request request) {

        final RequestOperation operation = new RequestOperation(this, request);
        queue(operation);

        long timeout = request.getTimeout();
        if (timeout > 0) {
            // TODO: add cancellation of timeouts
            Runnable timeoutOperation = new Runnable() {
                @Override
                public void run() {
                    operation.fail(new TimeoutException("request timed out"));
                }
            };
            timeout = Math.max(1, TimeUnit.MILLISECONDS.convert(timeout, request.getTimeoutUnit()));
            RunLoop.instance().schedule(timeoutOperation, timeout, TimeUnit.MILLISECONDS);
        }

        return operation;
    }

    /**
     * Enqueues a {@link RequestOperation} on the appropriate {@link SessionManager}.
     *
     * May be called more than once on a single operation due to redirects and
     * retries.
     */
    void queue(final RequestOperation operation) {

        Origin requestOrigin = new Origin(operation.getCurrentRequest().getUrl());
        Origin aliasedOrigin = aliases.get(requestOrigin);
        final Origin origin = aliasedOrigin != null ? aliasedOrigin : requestOrigin;

        RunLoop.instance().execute(new Runnable() {
            @Override
            public void run() {

                SessionManager manager = managers.get(origin);
                if (manager == null) {
                    manager = new SessionManager(origin, configuration);
                    managers.put(origin, manager);
                }

                manager.queue(operation);
            }
        });
        RunLoop.instance().startThread();
    }

    /**
     * Adds an alias from one origin to another.
     *
     * Requests with a hostname matching the alias will instead be sent over
     * a connection to the target origin. Host headers will remain unchanged.
     * If the target origin contains a raw IP address, X.509 certificate
     * validation for a new connection will be performed using the hostname
     * of the alias. Otherwise the connection will use the properties of the
     * target origin for certificate validation.
     *
     * Note: this is an advanced feature allowing more than one domain to
     * be multiplexed over a single connection and/or DNS to be bypassed,
     * and has security ramifications that should be well understood before
     * use in a production application.
     */
    public void addAlias(Origin alias, Origin target) {
        aliases.put(alias, target);
    }

    /**
     * Removes an alias to another origin.
     */
    public void removeAlias(Origin alias) {
        aliases.remove(alias);
    }

    /**
     * @return true if this client has been shut down.
     */
    public boolean isShutdown() {
        return false;
    }

    /**
     * @return true if all outstanding requests are completed and connections have
     *         closed following shutdown.
     */
    public boolean isTerminated() {
        return false;
    }


    /**
     * Attempt to complete all submitted requests, but stop accepting new requests.
     */
    public void shutdown() {
    }

    /**
     * Attempt to gracefully cancel all in-flight requests and close all open connections.
     */
    public void shutdownNow() {
    }

    /**
     * Immediately terminate all connections and fail in-flight requests.
     */
    public void terminate() {
    }
}
