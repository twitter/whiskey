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
public class SpdyProtocolException extends IOException {
    SpdyProtocolException(String message) {
        super(message);
    }
    SpdyProtocolException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
