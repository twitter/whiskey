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

abstract class SpdyHeaderBlockDecoder {

    /**
     * Decodes a SPDY Header Block, adding the Name/Value pairs to the given Headers frame.
     * If the header block is malformed, the Headers frame will be marked as invalid.
     * A stream error with status code PROTOCOL_ERROR must be issued in response to an invalid frame.
     *
     * @param headerBlock the header block to decode
     * @param streamId the id of the stream associated with this header block
     * @throws Exception If the header block is malformed in a way that prevents any future
     *                   decoding of any other header blocks, an exception will be thrown.
     *                   A session error with status code PROTOCOL_ERROR must be issued.
     */
    abstract void decode(ByteBuffer headerBlock, int streamId) throws Exception;

    abstract void endHeaderBlock();

    abstract void end();
}
