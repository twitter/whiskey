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
public class SpdySessionException extends IOException {

    private static final String[] statusMessages = {
            "SpdySession closed: graceful teardown",
            "SpdySession closed: protocol error",
            "SpdySession closed: internal error",
            "SpdySession closed: unknown error",
    };

    SpdySessionException(int statusCode) {
        super(statusMessage(statusCode));
    }

    SpdySessionException(String message) {
        super(message);
    }

    SpdySessionException(String message, Throwable throwable) {
        super(message, throwable);
    }

    private static String statusMessage(int statusCode) {
        if (statusCode < 0 || statusCode >= 3) statusCode = 3;
        return statusMessages[statusCode];
    }
}
