/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.net;

import com.twitter.whiskey.futures.ObservableFuture;

import java.nio.ByteBuffer;

/**
 * Future representing the body of an HTTP response. The observable elements
 * are ByteBuffers of data streamed as they are received. The resulting type
 * is the complete, assembled response body also as a ByteBuffer. To conserve
 * memory, once an Observer or Iterator is registered to consume the streamed
 * body, no further accumulation of the entire result will be performed and
 * any memory allocated for that purpose will be released to the GC.
 *
 * @author Michael Schore
 */
public interface BodyFuture extends ObservableFuture<ByteBuffer, ByteBuffer> {
}
