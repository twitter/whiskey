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
import java.util.zip.Deflater;

import static com.twitter.whiskey.net.SpdyCodecUtil.*;

class SpdyHeaderBlockZlibEncoder extends SpdyHeaderBlockRawEncoder {

    private final Deflater compressor;

    private boolean finished;

    SpdyHeaderBlockZlibEncoder(SpdyVersion spdyVersion, int compressionLevel) {
        super(spdyVersion);
        if (compressionLevel < 0 || compressionLevel > 9) {
            throw new IllegalArgumentException(
                    "compressionLevel: " + compressionLevel + " (expected: 0-9)");
        }
        compressor = new Deflater(compressionLevel);
        compressor.setDictionary(SPDY_DICT);
    }

    private int setInput(ByteBuffer uncompressed) {
        int len = uncompressed.remaining();

        if (uncompressed.hasArray()) {
            compressor.setInput(uncompressed.array(), uncompressed.arrayOffset() + uncompressed.position(), len);
        } else {
            byte[] in = new byte[len];
            uncompressed.get(in);
            compressor.setInput(in, 0, in.length);
        }

        return len;
    }

    private ByteBuffer encode(int len) {
        ByteBuffer compressed = ByteBuffer.allocate(2 * len);
        while (compressInto(compressed)) {
            // TODO:
            // Although unlikely, it's possible that the compressed size is larger than the decompressed size
//                compressed.ensureWritable(compressed.capacity() << 1);
        }
        compressed.flip();
        return compressed;
    }

    private boolean compressInto(ByteBuffer compressed) {
        byte[] out = compressed.array();
        int off = compressed.arrayOffset() + compressed.position();
        int toWrite = compressed.remaining();
        int numBytes = compressor.deflate(out, off, toWrite, Deflater.SYNC_FLUSH);
        compressed.position(compressed.position() + numBytes);
        return numBytes == toWrite;
    }

    @Override
    public ByteBuffer encode(Headers headers) throws Exception {
        if (headers == null) {
            throw new IllegalArgumentException("headers");
        }

        if (finished) {
            throw new RuntimeException("invalid compressor state");
        }

        ByteBuffer uncompressed = super.encode(headers);
        if (!uncompressed.hasRemaining()) {
            return ByteBuffer.allocate(0);
        }

        int len = setInput(uncompressed);
        return encode(len);
    }

    @Override
    public void end() {
        if (finished) {
            return;
        }
        finished = true;
        compressor.end();
        super.end();
    }
}
