/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.net;

import com.twitter.whiskey.nio.RunLoop;
import com.twitter.whiskey.nio.Socket;
import com.twitter.whiskey.nio.SSLSocket;
import com.twitter.whiskey.futures.Inline;
import com.twitter.whiskey.util.LinkedHashDeque;
import com.twitter.whiskey.util.Origin;
import com.twitter.whiskey.util.UniqueMultiMap;

import java.net.ConnectException;
import java.security.NoSuchAlgorithmException;
import java.util.Deque;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

class SessionManager {

    private final Origin origin;
    private final ClientConfiguration configuration;
    private final Deque<RequestOperation> pendingOperations = new LinkedHashDeque<>();
    private final SSLContext sslContext;
    private final UniqueMultiMap<Integer, Socket> pendingSocketMap = new UniqueMultiMap<>();
    private final UniqueMultiMap<Integer, Session> openSessionMap = new UniqueMultiMap<>();
    private final int parallelism;
    private final boolean secure;

    private static final int OFFLINE = -1;
    private static final int GENERIC = 0;

    // TODO: update connectivity via ConnectivityManager/BroadcastReceiver/etc
    // TODO: connect new sockets on connectivity change if requests are pending
    private volatile int connectivity = GENERIC;

    SessionManager(Origin origin, ClientConfiguration configuration) {

        this.configuration = configuration;
        this.origin = origin;
        this.parallelism = configuration.getMaxTcpConnections();
        secure = origin.getScheme().equals("https");
        sslContext = secure ? configuration.getSslContext() : null;
    }

    void queue(final RequestOperation operation) {

        final int currentConnectivity = connectivity;
        if (currentConnectivity == OFFLINE) {
            // TODO: determine exception/message
            operation.fail(new ConnectException("unable to connect to host"));
            return;
        }

        int openSessionCount = openSessionMap.get(currentConnectivity).size();

        // If an active session with capacity is available in the pool, dispatch the request
        // operation to it. Rotate sessions to distribute load across the pool.
        Session session;
        for (int i = 0; i < openSessionCount; i++) {
            session = openSessionMap.removeFirst(currentConnectivity);
            if (!session.isClosed()) {
                openSessionMap.put(currentConnectivity, session);
                if (session.isActive() && session.getCapacity() > 0) {
                    session.queue(operation);
                    return;
                }
            }
        }

        // If no active sessions are available, queue the operation locally.
        // Listen for cancellation/timeout.
        operation.addListener(new Inline.Listener<Response>() {
            @Override
            public void onError(Throwable throwable) {
                if (pendingOperations.contains(operation)) {
                    pendingOperations.remove(operation);
                }
            }
        });

        pendingOperations.add(operation);

        // If parallelism allows, open new socket connection(s).
        openSessionCount = openSessionMap.get(currentConnectivity).size();
        for (int i = 0; i < parallelism - openSessionCount; i++) {
            createSocket(currentConnectivity);
        }
    }

    void poll(Session session, int capacity) {
        for (int i = 0; i < capacity && i < pendingOperations.size(); i++) {
            session.queue(pendingOperations.poll());
        }
    }

    public Origin getOrigin() {
        return origin;
    }

    private void failOperations(Throwable e) {

        RequestOperation operation;
        while ((operation = pendingOperations.poll()) != null) {
            operation.fail(e);
        }
    }

    private SSLEngine newSslEngine() throws NoSuchAlgorithmException {

        SSLEngine engine;
        if (sslContext != null) {
            engine = sslContext.createSSLEngine();
        } else {
            engine = SSLContext.getDefault().createSSLEngine();
        }

        // TODO: setup protocol negotiation
        return engine;
    }

    private void createSocket(final int connectivity) {

        final Socket socket;
        if (secure) {
            SSLEngine engine;
            try {
                engine = newSslEngine();
            } catch (NoSuchAlgorithmException e) {
                if (openSessionMap.isEmpty()) {
                    failOperations(new ConnectException("SSLContext unavailable"));
                }
                return;
            }

            socket = new SSLSocket(origin, RunLoop.instance(), engine);
        } else {
            socket = new Socket(origin, RunLoop.instance());
        }

        pendingSocketMap.put(connectivity, socket);
        socket.connect().addListener(new Inline.Listener<Origin>() {
            @Override
            public void onComplete(Origin result) {
                pendingSocketMap.removeValue(socket);
                createSession(socket);
            }

            @Override
            public void onError(Throwable throwable) {
                pendingSocketMap.removeValue(socket);

                // If connectivity has changed operations may still succeed.
                // Otherwise, if there are no more pending sockets assume we
                // can't connect for now and fail operations.
                if (SessionManager.this.connectivity == connectivity &&
                    !pendingSocketMap.containsKey(connectivity) &&
                    openSessionMap.isEmpty()) {
                    failOperations(throwable);
                }
            }
        });
    }

    private void createSession(final Socket socket) {

        final int currentConnectivity = connectivity;
        final Session session;
        switch(socket.getProtocol()) {
            case SPDY_3_1:
                session = new SpdySession(this, configuration, socket);
                break;
            default:
                throw new RuntimeException("unsupported protocol");
        }

        openSessionMap.put(currentConnectivity, session);
        session.addCloseListener(new Inline.Listener<Void>() {
            @Override
            public void onComplete(Void result) {
                openSessionMap.removeValue(session);
            }

            @Override
            public void onError(Throwable throwable) {
                openSessionMap.removeValue(session);
            }
        });

        // TODO: implement load balancing delay
        while (session.getCapacity() > 0 && !pendingOperations.isEmpty()) {
            session.queue(pendingOperations.poll());
        }
    }
}
