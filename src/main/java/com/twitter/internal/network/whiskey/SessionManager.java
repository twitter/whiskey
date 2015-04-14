package com.twitter.internal.network.whiskey;


import java.net.ConnectException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

class SessionManager {

    private final Origin origin;
    private final ClientConfiguration configuration;
    private final Deque<RequestOperation> pendingOperations = new LinkedHashDeque<>();
    private final UniqueMultiMap<Integer, Socket> pendingSocketMap = new UniqueMultiMap<>();
    private final UniqueMultiMap<Integer, Session> openSessionMap = new UniqueMultiMap<>();

    private static final int OFFLINE = -1;
    private static final int GENERIC = 0;

    // TODO: move to per-interface, per-origin configuration
    private static final int MAX_PARALLELISM = 1;

    // TODO: update connectivity via ConnectivityManager/BroadcastReceiver/etc
    private volatile int connectivity = GENERIC;

    SessionManager(Origin origin, ClientConfiguration configuration) {

        this.configuration = configuration;
        this.origin = origin;
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
        operation.addListener(new Listener<Response>() {
            @Override
            public void onComplete(Response result) {
            }

            @Override
            public void onError(Throwable throwable) {
                if (pendingOperations.contains(operation)) {
                    pendingOperations.remove(operation);
                }
            }

            @Override
            public Executor getExecutor() {
                return Inline.INSTANCE;
            }
        });

        pendingOperations.add(operation);

        // If parallelism allows, open new socket connection(s).
        openSessionCount = openSessionMap.get(currentConnectivity).size();
        for (int i = 0; i < MAX_PARALLELISM - openSessionCount; i++) {
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

    private void createSocket(final int connectivity) {

        final Socket socket;
        if (origin.getScheme().equals("http")) {
            socket = new Socket(origin, RunLoop.instance());
        } else {
            SSLContext context;
            SSLEngine engine;
            try {
                // TODO: read context from config
//                context = SSLContext.getInstance("TLS");
//                context.init(null, null, null);
                context = SSLContext.getDefault();
                engine = context.createSSLEngine();
//            } catch (NoSuchAlgorithmException | NullPointerException | KeyManagementException e) {
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            socket = new SSLSocket(origin, RunLoop.instance(), engine);
        }
        pendingSocketMap.put(connectivity, socket);
        socket.connect().addListener(new Listener<Origin>() {
            @Override
            public void onComplete(Origin result) {
                pendingSocketMap.removeValue(socket);
                createSession(socket);
            }

            @Override
            public void onError(Throwable throwable) {
                pendingSocketMap.removeValue(socket);

                // Re-attempt if conncetivity has changed
                if (SessionManager.this.connectivity != connectivity) {
                    createSocket(SessionManager.this.connectivity);
                }
            }

            @Override
            public Executor getExecutor() {
                return Inline.INSTANCE;
            }
        });
    }

    private void createSession(final Socket socket) {

        final int currentConnectivity = connectivity;
        Session session;
        switch(socket.getProtocol()) {
            case SPDY_3_1:
                session = new SpdySession(this, configuration, socket);
                break;
            default:
                throw new RuntimeException("unsupported protocol");
        }

        openSessionMap.put(currentConnectivity, session);

        // TODO: implement load balancing delay
        while (session.getCapacity() > 0 && !pendingOperations.isEmpty()) {
            session.queue(pendingOperations.poll());
        }
    }
}
