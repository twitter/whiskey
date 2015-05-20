/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.net;

import java.io.IOException;

/**
 * @author Michael Schore
 */
public class SpdyStreamException extends IOException {

    private static final String[] statusMessages = {
        "unknown error",
        "protocol error",
        "invalid stream",
        "refused stream",
        "unsupported version",
        "cancel",
        "internal error",
        "flow control error",
        "stream in use",
        "stream already closed",
        "invalid credentials",
        "frame too large",
    };

    SpdyStreamException(int statusCode) {
        super(statusMessage(statusCode));
    }

    SpdyStreamException(String message) {
        super(message);
    }

    SpdyStreamException(String message, Throwable throwable) {
        super(message, throwable);
    }

    private static String statusMessage(int statusCode) {
        if (statusCode <= 0 || statusCode >= statusMessages.length) statusCode = 0;
        return "SpdyStream reset: " + statusMessages[statusCode];
    }
}
