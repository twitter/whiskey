package com.twitter.whiskey.net;

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
