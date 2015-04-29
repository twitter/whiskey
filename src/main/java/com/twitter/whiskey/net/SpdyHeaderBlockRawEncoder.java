/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.twitter.whiskey.net;

import static com.twitter.whiskey.net.SpdyCodecUtil.SPDY_MAX_NV_LENGTH;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Set;

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
