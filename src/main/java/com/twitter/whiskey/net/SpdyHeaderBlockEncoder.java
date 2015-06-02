/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is substantially based on work from the Netty project, also
 * released under the above license.
 */

package com.twitter.whiskey.net;

import java.nio.ByteBuffer;

/**
 * @author Michael Schore
 */
abstract class SpdyHeaderBlockEncoder {

    /**
     * Encodes SPDY {@link Headers} into a {@link ByteBuffer}.
     */
    abstract ByteBuffer encode(Headers headers) throws Exception;
    abstract void end();
}
