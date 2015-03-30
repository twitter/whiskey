package com.twitter.internal.network.whiskey;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

/**
 * Interface used by {@link RunLoop} to notify socket implementations of state changes.
 */
abstract class SelectableSocket {

    /**
     * Called when the socket has either finished or has failed to finish connecting.
     */
    abstract void onConnect();

    /**
     * Called when the socket is ready for reading
     */
    abstract void onReadable();

    /**
     * Called when the socket is ready for writing
     */
    abstract void onWriteable();

    /**
     * Called when the socket has been closed
     */
    abstract void onClose(Throwable e);

    /**
     * @return The {@link SelectableChannel} this Socket is bound to
     */
    abstract SelectableChannel getChannel();

    /**
     * Sets the current {@link SelectionKey} for the socket.
     */
    abstract void setSelectionKey(SelectionKey key);
}
