package com.twitter.internal.network.whiskey;

import java.io.IOException;

/**
 * @author Michael Schore
 */
public class SpdyProtocolException extends IOException {
    SpdyProtocolException(String message) {
        super(message);
    }
    SpdyProtocolException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
