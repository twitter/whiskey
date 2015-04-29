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

    public void addAlias(Origin alias, Origin origin) {
        aliases.put(alias, origin);
    }

    // TODO: rename to shutdown and terminate similar to ExecutorService interface
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
