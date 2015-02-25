package com.twitter.internal.network.whiskey;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

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

    public ResponseFuture submit(Request request) {

        final RequestOperation operation = new RequestOperation(this, request);
        queue(operation);
        return operation;
    }

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
    }

    public void addAlias(Origin alias, Origin origin) {
        aliases.put(alias, origin);
    }

    /**
     * Attempt to gracefully cancel all in-flight requests and close all open connections.
     */
    public void close() {

    }

    /**
     * Immediately terminate all connections and fail in-flight requests.
     */
    public void kill() {

    }
}
