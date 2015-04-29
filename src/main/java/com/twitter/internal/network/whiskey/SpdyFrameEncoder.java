package com.twitter.internal.network.whiskey;

/*
 * Copyright 2014 The Netty Project
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
import java.nio.ByteOrder;
import java.util.Set;

import static com.twitter.internal.network.whiskey.SpdyCodecUtil.*;

/**
 * Encodes a SPDY Frame into a {@link ByteBuffer}.
 */
class SpdyFrameEncoder {

    private final SpdyHeaderBlockEncoder headerBlockEncoder;
    private final int version;

    /**
     * Creates a new instance with the specified {@code spdyVersion}.
     */
    public SpdyFrameEncoder(SpdyVersion spdyVersion) {
        if (spdyVersion == null) {
            throw new NullPointerException("spdyVersion");
        }
        headerBlockEncoder = new SpdyHeaderBlockZlibEncoder(spdyVersion, 9);
        version = spdyVersion.getVersion();
    }

    private void writeControlFrameHeader(ByteBuffer buffer, int type, byte flags, int length) {
        buffer.putShort((short) (version | 0x8000));
        buffer.putShort((short) type);
        buffer.put(flags);
        writeMedium(buffer, length);
    }

    public void writeMedium(ByteBuffer buffer, int medium) {
        buffer.put((byte) (medium >>> 16));
        buffer.put((byte) (medium >>> 8));
        buffer.put((byte) medium);
    }

    public ByteBuffer[] encodeDataFrame(int streamId, boolean last, ByteBuffer data) {
        byte flags = last ? SPDY_DATA_FLAG_FIN : 0;
        int length = data.limit();
        ByteBuffer frame = ByteBuffer.allocateDirect(SPDY_HEADER_SIZE).order(ByteOrder.BIG_ENDIAN);
        frame.putInt(streamId & 0x7FFFFFFF);
        frame.put(flags);
        writeMedium(frame, length);
        frame.flip();
        return new ByteBuffer[]{ frame, data };
    }

    public ByteBuffer[] encodeSynStreamFrame(int streamId, int associatedToStreamId,
            byte priority, boolean last, boolean unidirectional, Headers headers) {
        ByteBuffer headerBlock;
        try {
            headerBlock = headerBlockEncoder.encode(headers);
        } catch (Exception e) {
            headerBlock = ByteBuffer.allocate(0);
            System.err.println(e.toString());
        }
        int headerBlockLength = headerBlock.limit();
        byte flags = last ? SPDY_FLAG_FIN : 0;
        if (unidirectional) {
            flags |= SPDY_FLAG_UNIDIRECTIONAL;
        }
        int length = 10 + headerBlockLength;
        ByteBuffer frame = ByteBuffer.allocateDirect(SPDY_HEADER_SIZE + 10).order(ByteOrder.BIG_ENDIAN);
        writeControlFrameHeader(frame, SPDY_SYN_STREAM_FRAME, flags, length);
        frame.putInt(streamId);
        frame.putInt(associatedToStreamId);
        frame.putShort((short) ((priority & 0xFF) << 13));
        frame.flip();
        return new ByteBuffer[]{ frame, headerBlock };
    }

    public ByteBuffer[] encodeSynReplyFrame(int streamId, boolean last, Headers headers) {
        ByteBuffer headerBlock;
        try {
            headerBlock = headerBlockEncoder.encode(headers);
        } catch (Exception e) {
            headerBlock = ByteBuffer.allocate(0);
            System.err.println(e.toString());
        }
        int headerBlockLength = headerBlock.limit();
        byte flags = last ? SPDY_FLAG_FIN : 0;
        int length = 4 + headerBlockLength;
        ByteBuffer frame = ByteBuffer.allocateDirect(SPDY_HEADER_SIZE + 4).order(ByteOrder.BIG_ENDIAN);
        writeControlFrameHeader(frame, SPDY_SYN_REPLY_FRAME, flags, length);
        frame.putInt(streamId);
        frame.flip();
        return new ByteBuffer[]{ frame, headerBlock };
    }

    public ByteBuffer encodeRstStreamFrame(int streamId, int statusCode) {
        byte flags = 0;
        int length = 8;
        ByteBuffer frame = ByteBuffer.allocateDirect(SPDY_HEADER_SIZE + length).order(ByteOrder.BIG_ENDIAN);
        writeControlFrameHeader(frame, SPDY_RST_STREAM_FRAME, flags, length);
        frame.putInt(streamId);
        frame.putInt(statusCode);
        frame.flip();
        return frame;
    }

    public ByteBuffer encodeSettingsFrame(SpdySettings spdySettings) {
        Set<Integer> ids = spdySettings.ids();
        int numSettings = ids.size();

        byte flags = spdySettings.clearPreviouslyPersistedSettings() ?
                SPDY_SETTINGS_CLEAR : 0;
        int length = 4 + 8 * numSettings;
        ByteBuffer frame = ByteBuffer.allocateDirect(SPDY_HEADER_SIZE + length).order(ByteOrder.BIG_ENDIAN);
        writeControlFrameHeader(frame, SPDY_SETTINGS_FRAME, flags, length);
        frame.putInt(numSettings);
        for (Integer id : ids) {
            flags = 0;
            if (spdySettings.isPersistValue(id)) {
                flags |= SPDY_SETTINGS_PERSIST_VALUE;
            }
            if (spdySettings.isPersisted(id)) {
                flags |= SPDY_SETTINGS_PERSISTED;
            }
            frame.put(flags);
            writeMedium(frame, id);
            frame.putInt(spdySettings.getValue(id));
        }
        frame.flip();
        return frame;
    }

    public ByteBuffer encodePingFrame(int id) {
        byte flags = 0;
        int length = 4;
        ByteBuffer frame = ByteBuffer.allocateDirect(SPDY_HEADER_SIZE + length).order(ByteOrder.BIG_ENDIAN);
        writeControlFrameHeader(frame, SPDY_PING_FRAME, flags, length);
        frame.putInt(id);
        frame.flip();
        return frame;
    }

    public ByteBuffer encodeGoAwayFrame(int lastGoodStreamId, int statusCode) {
        byte flags = 0;
        int length = 8;
        ByteBuffer frame = ByteBuffer.allocateDirect(SPDY_HEADER_SIZE + length).order(ByteOrder.BIG_ENDIAN);
        writeControlFrameHeader(frame, SPDY_GOAWAY_FRAME, flags, length);
        frame.putInt(lastGoodStreamId);
        frame.putInt(statusCode);
        frame.flip();
        return frame;
    }

    public ByteBuffer[] encodeHeadersFrame(int streamId, boolean last, Headers headers) {
        ByteBuffer headerBlock;
        try {
            headerBlock = headerBlockEncoder.encode(headers);
        } catch (Exception e) {
            headerBlock = ByteBuffer.allocate(0);
            System.err.println(e.toString());
        }
        int headerBlockLength = headerBlock.limit();
        byte flags = last ? SPDY_FLAG_FIN : 0;
        int length = 4 + headerBlockLength;
        ByteBuffer frame = ByteBuffer.allocateDirect(SPDY_HEADER_SIZE + 4).order(ByteOrder.BIG_ENDIAN);
        writeControlFrameHeader(frame, SPDY_HEADERS_FRAME, flags, length);
        frame.putInt(streamId);
        frame.flip();
        return new ByteBuffer[]{ frame, headerBlock };
    }

    public ByteBuffer encodeWindowUpdateFrame(int streamId, int deltaWindowSize) {
        byte flags = 0;
        int length = 8;
        ByteBuffer frame = ByteBuffer.allocateDirect(SPDY_HEADER_SIZE + length).order(ByteOrder.BIG_ENDIAN);
        writeControlFrameHeader(frame, SPDY_WINDOW_UPDATE_FRAME, flags, length);
        frame.putInt(streamId);
        frame.putInt(deltaWindowSize);
        frame.flip();
        return frame;
    }
}
