package com.twitter.internal.network.whiskey;

import java.nio.channels.SelectableChannel;

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
    public void onClose();

    /**
     * @return The <code>SelectableChannel</code> this Socket is bound to
     */
    public SelectableChannel getChannel();

}
