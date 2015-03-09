package com.twitter.internal.network.whiskey;


import java.net.ConnectException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

class SessionManager {

    private final Origin origin;
    private final ClientConfiguration configuration;
    // TODO: implement HashDeque
    private final Deque<RequestOperation> pendingOperations = new ArrayDeque<>();
    private final Map<Integer, Deque<Socket>> pendingSocketMap;
    private final Map<Integer, Deque<Session>> openSessionMap;
    private final Map<Session, Integer> inverseSessionMap;

    private static final int OFFLINE = -1;
    private static final int GENERIC = 0;

    // TODO: move to per-interface, per-origin configuration
    private static final int MAX_PARALLELISM = 1;

    // TODO: update connectivity via ConnectivityManager/BroadcastReceiver/etc
    private volatile int connectivity = GENERIC;

    SessionManager(Origin origin, ClientConfiguration configuration) {

        this.configuration = configuration;
        this.origin = origin;
        pendingSocketMap = new HashMap<>(2);
        openSessionMap = new HashMap<>(2);
        inverseSessionMap = new HashMap<>(4);
    }

    void queue(final RequestOperation operation) {

        final int currentConnectivity = connectivity;
        if (currentConnectivity == OFFLINE) {
            // TODO: determine exception/message
            operation.fail(new ConnectException("unable to connect to host"));
            return;
        }

        // Locate the session pool associated with device's current preferred network interface
        Deque<Session> openSessions = openSessionMap.get(currentConnectivity);
        if (openSessions == null) {
            openSessions = new ArrayDeque<>();
            openSessionMap.put(currentConnectivity, openSessions);
        }

        // If an active session with capacity is available in the pool, dispatch the request
        // operation to it. Rotate sessions to distribute load across the pool.
        Session session;
        for (int i = 0; i < openSessions.size(); i++) {
            session = openSessions.poll();
            if (!session.isClosed()) {
                openSessions.add(session);
                if (session.isActive() && session.getCapacity() > 0) {
                    session.queue(operation);
                    return;
                }
            } else {
                inverseSessionMap.remove(session);
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
        final int sessionCount = openSessions.size();
        for (int i = 0; i < MAX_PARALLELISM - sessionCount; i++) {
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

        Deque<Socket> pendingSockets = pendingSocketMap.get(connectivity);
        if (pendingSockets == null) {
            pendingSockets = new ArrayDeque<>();
            pendingSocketMap.put(connectivity, pendingSockets);
        }
        final Deque<Socket> fPendingSockets = pendingSockets;

        final Socket socket = new Socket(origin, RunLoop.instance());
        fPendingSockets.add(socket);
        socket.connect().addListener(new Listener<Origin>() {
            @Override
            public void onComplete(Origin result) {
                fPendingSockets.remove(socket);
                createSession(socket);
            }

            @Override
            public void onError(Throwable throwable) {
                // TODO: consider re-attempting, but be cautious of loops / runaway processing
//                if (SessionManager.this.connectivity != connectivity) {
//                    createSocket(SessionManager.this.connectivity);
//                }
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

        // Locate the session pool associated with device's current preferred network interface
        Deque<Session> openSessions = openSessionMap.get(currentConnectivity);
        if (openSessions == null) {
            openSessions = new ArrayDeque<>();
            openSessionMap.put(currentConnectivity, openSessions);
        }
        openSessions.add(session);

        // TODO: implement load balancing delay
        while (session.getCapacity() > 0 && !pendingOperations.isEmpty()) {
            session.queue(pendingOperations.poll());
        }
    }
}
