package com.twitter.whiskey.nio;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

/**
 * Interface used by {@link RunLoop} to notify implementations of state changes.
 */
abstract class Selectable {

    /**
     * Called when the source has either finished or failed connecting.
     */
    abstract void onConnect();

    /**
     * Called when the source is ready for reading
     */
    abstract void onReadable();

    /**
     * Called when the source is ready for writing
     */
    abstract void onWriteable();

    /**
     * Called when the source has been closed
     */
    abstract void onClose(Throwable e);

    /**
     * @return The {@link SelectableChannel} to which this source is associated
     */
    abstract SelectableChannel getChannel();

    /**
     * Sets the current {@link SelectionKey} for the source.
     */
    abstract void setSelectionKey(SelectionKey key);
}
