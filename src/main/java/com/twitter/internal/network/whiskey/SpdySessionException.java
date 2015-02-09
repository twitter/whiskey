package com.twitter.internal.network.whiskey;

import java.io.IOException;

/**
 * @author Michael Schore
 */
public class SpdySessionException extends IOException {
    public SpdySessionException(String message) {
        super(message);
    }
}
