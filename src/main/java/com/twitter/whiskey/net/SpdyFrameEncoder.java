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
import java.nio.ByteOrder;
import java.util.Set;

import static com.twitter.whiskey.net.SpdyCodecUtil.*;

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
