package com.twitter.internal.network.whiskey;

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

import java.nio.ByteBuffer;

/**
 * The default {@link SpdyDataFrame} implementation.
 */
class SpdyDataFrame extends SpdyStreamFrame {

    private final ByteBuffer data;

    /**
     * Creates a new instance.
     *
     * @param streamId the Stream-ID of this frame
     */
    public SpdyDataFrame(int streamId) {
        this(streamId, ByteBuffer.allocate(0));
    }

    /**
     * Creates a new instance.
     *
     * @param streamId  the Stream-ID of this frame
     * @param data      the payload of the frame. Can not exceed {@link SpdyCodecUtil#SPDY_MAX_LENGTH}
     */
    public SpdyDataFrame(int streamId, ByteBuffer data) {
        super(streamId);
        if (data == null) {
            throw new NullPointerException("data");
        }
        this.data = validate(data);
    }

    private static ByteBuffer validate(ByteBuffer data) {
        if (data.limit() > SpdyCodecUtil.SPDY_MAX_LENGTH) {
            throw new IllegalArgumentException("data payload cannot exceed "
                    + SpdyCodecUtil.SPDY_MAX_LENGTH + " bytes");
        }
        return data;
    }

    @Override
    public SpdyDataFrame setStreamId(int streamId) {
        super.setStreamId(streamId);
        return this;
    }

    @Override
    public SpdyDataFrame setLast(boolean last) {
        super.setLast(last);
        return this;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("SpdyDataFrame");
        buf.append("(last: ");
        buf.append(isLast());
        buf.append(')');
        buf.append("\n");
        buf.append("--> Stream-ID = ");
        buf.append(streamId());
        buf.append("\n");
        buf.append("--> Size = ");
        buf.append(data.limit());
        return buf.toString();
    }
}
