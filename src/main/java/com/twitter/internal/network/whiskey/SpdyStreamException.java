package com.twitter.internal.network.whiskey;

import java.io.IOException;

/**
 * @author Michael Schore
 */
public class SpdyStreamException extends IOException {
    public SpdyStreamException(String message) {
        super(message);
    }
}
