/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.net;

public class SpdyConstants {
    static final int PRIORITY_LEVELS = 8;
    static final int SPDY_SESSION_STREAM_ID = 0;
    static final int DEFAULT_INITIAL_WINDOW_SIZE = 65536;

    static final int SPDY_SESSION_OK = 0;
    static final int SPDY_SESSION_PROTOCOL_ERROR = 1;
    static final int SPDY_SESSION_INTERNAL_ERROR = 2;

    static final int SPDY_STREAM_PROTOCOL_ERROR = 1;
    static final int SPDY_STREAM_INVALID_STREAM = 2;
    static final int SPDY_STREAM_REFUSED_STREAM = 3;
    static final int SPDY_STREAM_UNSUPPORTED_VERSION = 4;
    static final int SPDY_STREAM_CANCEL = 5;
    static final int SPDY_STREAM_INTERNAL_ERROR = 6;
    static final int SPDY_STREAM_FLOW_CONTROL_ERROR = 7;
    static final int SPDY_STREAM_STREAM_IN_USE = 8;
    static final int SPDY_STREAM_STREAM_ALREADY_CLOSED = 9;
    static final int SPDY_STREAM_INVALID_CREDENTIALS = 10;
    static final int SPDY_STREAM_FRAME_TOO_LARGE = 11;
}
