package com.twitter.internal.network.whiskey;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

/**
 * Provides notification of Socket state changes.
 */
interface SelectableSocket {

    /**
     * Called when the Socket has either finished or has failed to finish connecting.
     */
    public void onConnect();

    /**
     * Called when the Socket is ready for reading
     */
    public void onReadable();

    /**
     * Called when the Socket is ready for writing
     */
    public void onWriteable();

    /**
     * Called when the Socket has been closed
     */
    public void onClose(Throwable e);

    /**
     * @return The <code>SelectableChannel</code> this Socket is bound to
     */
    public SelectableChannel getChannel();

    /**
     * Sets the current {@link SelectionKey} for the socket.
     */
    public void setSelectionKey(SelectionKey key);
}
