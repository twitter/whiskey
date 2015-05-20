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
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static com.twitter.whiskey.net.SpdyCodecUtil.SPDY_MAX_NV_LENGTH;

public class SpdyHeaderBlockRawEncoder extends SpdyHeaderBlockEncoder {

    public SpdyHeaderBlockRawEncoder(SpdyVersion version) {
        if (version == null) {
            throw new NullPointerException("version");
        }
    }

    @Override
    public ByteBuffer encode(Headers headers) throws Exception {
        Set<String> names = headers.keySet();
        int numHeaders = names.size();
        if (numHeaders == 0) {
            return ByteBuffer.allocate(0);
        }
        if (numHeaders > SPDY_MAX_NV_LENGTH) {
            throw new IllegalArgumentException(
                    "header block contains too many headers");
        }

        // TODO: improve allocation
        ByteBuffer headerBlock = ByteBuffer.allocate(32768);
        headerBlock.putInt(numHeaders);

        for (String name : names) {
            byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);

            headerBlock.putInt(nameBytes.length);
            headerBlock.put(nameBytes);

            int savedIndex = headerBlock.position();
            int valueLength = 0;
            headerBlock.position(savedIndex + 4);

            for (String value : headers.get(name)) {
                byte[] valueBytes = value.getBytes(StandardCharsets.US_ASCII);
                if (valueBytes.length > 0) {
                    headerBlock.put(valueBytes);
                    headerBlock.put((byte) 0);
                    valueLength += valueBytes.length + 1;
                }
            }
            if (valueLength != 0) {
                valueLength --;
            }
            if (valueLength > SPDY_MAX_NV_LENGTH) {
                throw new IllegalArgumentException(
                        "header exceeds allowable length: " + name);
            }
            if (valueLength > 0) {
                headerBlock.putInt(savedIndex, valueLength);
                headerBlock.position(headerBlock.position() - 1);
            }
        }

        headerBlock.flip();
        return headerBlock;
    }

    @Override
    void end() {
    }
}
