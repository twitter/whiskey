package com.twitter.internal.network.whiskey;

/**
 * @author Michael Schore
 *
 * Session states: OPEN = [CONNECTED, ACTIVE], CLOSED = [DRAINING, DISCONNECTED]
 * CONNECTED: all sessions start in this state with a connected socket
 * ACTIVE: a session becomes active when it successfully retrieves a complete http response
 * DRAINING: the session is still connected and handling in-flight requests, but will accepat no further new requests
 * DISCONNECTED: the underlying socket is disconnected
 */
interface Session {
    /**
     * @return true if the session can handle future requests
     */
    boolean isOpen();

    /**
     * @return true while the underlying socket is connected
     */
    boolean isConnected();

    /**
     * @return true if the session is open and has successfully retrieved a complete http response
     */
    boolean isActive();

    /**
     * @return true if the session can handle no further requests
     */
    boolean isClosed();

    /**
     * @return true if the session is closed, but still connected
     */
    boolean isDraining();

    /**
     * @return true when the underlying socket is disconnected
     */
    boolean isDisconnected();

    /**
     * @return the number of additional requests the session has capacity to handle
     */
    int getCapacity();

    /**
     * @return true if the session ever successfully retrieved a complete http response
     */
    boolean wasActive();

    /**
     * Queue a {@link RequestOperation} to be handled by the session.
     */
    void queue(RequestOperation operation);
}
