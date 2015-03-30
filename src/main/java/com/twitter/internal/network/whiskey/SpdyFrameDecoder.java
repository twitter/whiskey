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

import static com.twitter.internal.network.whiskey.SpdyCodecUtil.SPDY_DATA_FLAG_FIN;
import static com.twitter.internal.network.whiskey.SpdyCodecUtil.SPDY_DATA_FRAME;
import static com.twitter.internal.network.whiskey.SpdyCodecUtil.SPDY_FLAG_FIN;
import static com.twitter.internal.network.whiskey.SpdyCodecUtil.SPDY_FLAG_UNIDIRECTIONAL;
import static com.twitter.internal.network.whiskey.SpdyCodecUtil.SPDY_GOAWAY_FRAME;
import static com.twitter.internal.network.whiskey.SpdyCodecUtil.SPDY_HEADERS_FRAME;
import static com.twitter.internal.network.whiskey.SpdyCodecUtil.SPDY_HEADER_FLAGS_OFFSET;
import static com.twitter.internal.network.whiskey.SpdyCodecUtil.SPDY_HEADER_LENGTH_OFFSET;
import static com.twitter.internal.network.whiskey.SpdyCodecUtil.SPDY_HEADER_SIZE;
import static com.twitter.internal.network.whiskey.SpdyCodecUtil.SPDY_HEADER_TYPE_OFFSET;
import static com.twitter.internal.network.whiskey.SpdyCodecUtil.SPDY_PING_FRAME;
import static com.twitter.internal.network.whiskey.SpdyCodecUtil.SPDY_RST_STREAM_FRAME;
import static com.twitter.internal.network.whiskey.SpdyCodecUtil.SPDY_SETTINGS_CLEAR;
import static com.twitter.internal.network.whiskey.SpdyCodecUtil.SPDY_SETTINGS_FRAME;
import static com.twitter.internal.network.whiskey.SpdyCodecUtil.SPDY_SETTINGS_PERSISTED;
import static com.twitter.internal.network.whiskey.SpdyCodecUtil.SPDY_SETTINGS_PERSIST_VALUE;
import static com.twitter.internal.network.whiskey.SpdyCodecUtil.SPDY_SYN_REPLY_FRAME;
import static com.twitter.internal.network.whiskey.SpdyCodecUtil.SPDY_SYN_STREAM_FRAME;
import static com.twitter.internal.network.whiskey.SpdyCodecUtil.SPDY_WINDOW_UPDATE_FRAME;
import static com.twitter.internal.network.whiskey.SpdyCodecUtil.SPDY_SESSION_STREAM_ID;
import static com.twitter.internal.network.whiskey.SpdyCodecUtil.getUnsignedInt;
import static com.twitter.internal.network.whiskey.SpdyCodecUtil.getUnsignedMedium;
import static com.twitter.internal.network.whiskey.SpdyCodecUtil.getUnsignedShort;

/**
 * Decodes {@link ByteBuffer}s into SPDY Frames.
 */
public class SpdyFrameDecoder {

    private final SpdyFrameDecoderDelegate delegate;
    private final SpdyHeaderBlockDecoder headerBlockDecoder;
    private final int spdyVersion;
    private final int maxChunkSize;

    private State state;

    // SPDY common header fields
    private byte flags;
    private int length;
    private int streamId;

    private int numSettings;

    private enum State {
        READ_COMMON_HEADER,
        READ_DATA_FRAME,
        READ_SYN_STREAM_FRAME,
        READ_SYN_REPLY_FRAME,
        READ_RST_STREAM_FRAME,
        READ_SETTINGS_FRAME,
        READ_SETTING,
        READ_PING_FRAME,
        READ_GOAWAY_FRAME,
        READ_HEADERS_FRAME,
        READ_WINDOW_UPDATE_FRAME,
        READ_HEADER_BLOCK,
        DISCARD_FRAME,
        FRAME_ERROR
    }

    /**
     * Creates a new instance with the specified {@code version}
     * and the default {@code maxChunkSize (8192)}.
     */
    public SpdyFrameDecoder(SpdyVersion spdyVersion, SpdyFrameDecoderDelegate delegate) {
        this(spdyVersion, delegate, 8192);
    }

    /**
     * Creates a new instance with the specified parameters.
     */
    public SpdyFrameDecoder(SpdyVersion spdyVersion, SpdyFrameDecoderDelegate delegate, int maxChunkSize) {
        if (spdyVersion == null) {
            throw new NullPointerException("spdyVersion");
        }
        if (delegate == null) {
            throw new NullPointerException("delegate");
        }
        if (maxChunkSize <= 0) {
            throw new IllegalArgumentException(
                    "maxChunkSize must be a positive integer: " + maxChunkSize);
        }
        this.headerBlockDecoder = new SpdyHeaderBlockZlibDecoder(spdyVersion, delegate, 4096);
        this.spdyVersion = spdyVersion.getVersion();
        this.delegate = delegate;
        this.maxChunkSize = maxChunkSize;
        state = State.READ_COMMON_HEADER;
    }

    public void decode(ByteBuffer buffer) {
        boolean last;
        int statusCode;

        while (true) {
            switch(state) {
                case READ_COMMON_HEADER:
                    if (buffer.remaining() < SPDY_HEADER_SIZE) {
                        return;
                    }

                    int frameOffset  = buffer.position();
                    int flagsOffset  = frameOffset + SPDY_HEADER_FLAGS_OFFSET;
                    int lengthOffset = frameOffset + SPDY_HEADER_LENGTH_OFFSET;
                    buffer.position(frameOffset + SPDY_HEADER_SIZE);

                    boolean control = (buffer.get(frameOffset) & 0x80) != 0;

                    int version;
                    int type;
                    if (control) {
                        // Decode control frame common header
                        version = getUnsignedShort(buffer, frameOffset) & 0x7FFF;
                        type = getUnsignedShort(buffer, frameOffset + SPDY_HEADER_TYPE_OFFSET);
                        streamId = SPDY_SESSION_STREAM_ID; // Default to session Stream-ID
                    } else {
                        // Decode data frame common header
                        version = spdyVersion; // Default to expected version
                        type = SPDY_DATA_FRAME;
                        streamId = getUnsignedInt(buffer, frameOffset);
                    }

                    flags  = buffer.get(flagsOffset);
                    length = getUnsignedMedium(buffer, lengthOffset);

                    // Check version first then validity
                    if (version != spdyVersion) {
                        state = State.FRAME_ERROR;
                        delegate.readFrameError("Invalid SPDY Version");
                    } else if (!isValidFrameHeader(streamId, type, flags, length)) {
                        state = State.FRAME_ERROR;
                        delegate.readFrameError("Invalid Frame Error");
                    } else {
                        state = getNextState(type, length);
                    }
                    break;

                case READ_DATA_FRAME:
                    if (length == 0) {
                        state = State.READ_COMMON_HEADER;
                        delegate.readDataFrame(streamId, hasFlag(flags, SPDY_DATA_FLAG_FIN), ByteBuffer.allocate(0));
                        break;
                    }

                    // Generate data frames that do not exceed maxChunkSize
                    int dataLength = Math.min(maxChunkSize, length);

                    // TODO: consider removing this (and replacing with Math.min) for liveness
                    // Wait until entire frame is readable
                    if (buffer.remaining() < dataLength) {
                        return;
                    }

                    ByteBuffer data = ByteBuffer.allocate(dataLength);
                    data.put(buffer);
                    length -= dataLength;

                    if (length == 0) {
                        state = State.READ_COMMON_HEADER;
                    }

                    last = length == 0 && hasFlag(flags, SPDY_DATA_FLAG_FIN);

                    delegate.readDataFrame(streamId, last, data);
                    break;

                case READ_SYN_STREAM_FRAME:
                    if (buffer.remaining() < 10) {
                        return;
                    }

                    int offset = buffer.position();
                    streamId = getUnsignedInt(buffer, offset);
                    int associatedToStreamId = getUnsignedInt(buffer, offset + 4);
                    byte priority = (byte) (buffer.get(offset + 8) >> 5 & 0x07);
                    last = hasFlag(flags, SPDY_FLAG_FIN);
                    boolean unidirectional = hasFlag(flags, SPDY_FLAG_UNIDIRECTIONAL);
                    buffer.position(offset + 10);
                    length -= 10;

                    if (streamId == 0) {
                        state = State.FRAME_ERROR;
                        delegate.readFrameError("Invalid SYN_STREAM Frame");
                    } else {
                        state = State.READ_HEADER_BLOCK;
                        delegate.readSynStreamFrame(streamId, associatedToStreamId, priority, last, unidirectional);
                    }
                    break;

                case READ_SYN_REPLY_FRAME:
                    if (buffer.remaining() < 4) {
                        return;
                    }

                    streamId = getUnsignedInt(buffer);
                    last = hasFlag(flags, SPDY_FLAG_FIN);
                    length -= 4;

                    if (streamId == 0) {
                        state = State.FRAME_ERROR;
                        delegate.readFrameError("Invalid SYN_REPLY Frame");
                    } else {
                        state = State.READ_HEADER_BLOCK;
                        delegate.readSynReplyFrame(streamId, last);
                    }
                    break;

                case READ_RST_STREAM_FRAME:
                    if (buffer.remaining() < 8) {
                        return;
                    }

                    streamId = getUnsignedInt(buffer);
                    statusCode = buffer.getInt();

                    if (streamId == 0 || statusCode == 0) {
                        state = State.FRAME_ERROR;
                        delegate.readFrameError("Invalid RST_STREAM Frame");
                    } else {
                        state = State.READ_COMMON_HEADER;
                        delegate.readRstStreamFrame(streamId, statusCode);
                    }
                    break;

                case READ_SETTINGS_FRAME:
                    if (buffer.remaining() < 4) {
                        return;
                    }

                    boolean clear = hasFlag(flags, SPDY_SETTINGS_CLEAR);

                    numSettings = getUnsignedInt(buffer);
                    length -= 4;

                    // Validate frame length against number of entries. Each ID/Value entry is 8 bytes.
                    if ((length & 0x07) != 0 || length >> 3 != numSettings) {
                        state = State.FRAME_ERROR;
                        delegate.readFrameError("Invalid SETTINGS Frame");
                    } else {
                        state = State.READ_SETTING;
                        delegate.readSettingsFrame(clear);
                    }
                    break;

                case READ_SETTING:
                    if (numSettings == 0) {
                        state = State.READ_COMMON_HEADER;
                        delegate.readSettingsEnd();
                        break;
                    }

                    if (buffer.remaining() < 8) {
                        return;
                    }

                    byte settingsFlags = buffer.get();
                    int id = getUnsignedMedium(buffer);
                    int value = buffer.getInt();
                    boolean persistValue = hasFlag(settingsFlags, SPDY_SETTINGS_PERSIST_VALUE);
                    boolean persisted = hasFlag(settingsFlags, SPDY_SETTINGS_PERSISTED);
                    --numSettings;

                    delegate.readSetting(id, value, persistValue, persisted);
                    break;

                case READ_PING_FRAME:
                    if (buffer.remaining() < 4) {
                        return;
                    }

                    int pingId = buffer.getInt();

                    state = State.READ_COMMON_HEADER;
                    delegate.readPingFrame(pingId);
                    break;

                case READ_GOAWAY_FRAME:
                    if (buffer.remaining() < 8) {
                        return;
                    }

                    int lastGoodStreamId = getUnsignedInt(buffer);
                    statusCode = buffer.getInt();

                    state = State.READ_COMMON_HEADER;
                    delegate.readGoAwayFrame(lastGoodStreamId, statusCode);
                    break;

                case READ_HEADERS_FRAME:
                    if (buffer.remaining() < 4) {
                        return;
                    }

                    streamId = getUnsignedInt(buffer);
                    last = hasFlag(flags, SPDY_FLAG_FIN);
                    length -= 4;

                    if (streamId == 0) {
                        state = State.FRAME_ERROR;
                        delegate.readFrameError("Invalid HEADERS Frame");
                    } else {
                        state = State.READ_HEADER_BLOCK;
                        delegate.readHeadersFrame(streamId, last);
                    }
                    break;

                case READ_WINDOW_UPDATE_FRAME:
                    if (buffer.remaining() < 8) {
                        return;
                    }

                    streamId = getUnsignedInt(buffer);
                    int deltaWindowSize = getUnsignedInt(buffer);

                    if (deltaWindowSize == 0) {
                        state = State.FRAME_ERROR;
                        delegate.readFrameError("Invalid WINDOW_UPDATE Frame");
                    } else {
                        state = State.READ_COMMON_HEADER;
                        delegate.readWindowUpdateFrame(streamId, deltaWindowSize);
                    }
                    break;

                case READ_HEADER_BLOCK:
                    if (length == 0) {
                        state = State.READ_COMMON_HEADER;
                        headerBlockDecoder.endHeaderBlock();
                        break;
                    }

                    if (!buffer.hasRemaining()) {
                        return;
                    }

                    int headerBytes = Math.min(buffer.remaining(), length);

                    ByteBuffer headerBlock = buffer.slice();
                    headerBlock.limit(headerBytes);

                    try {
                        headerBlockDecoder.decode(headerBlock, streamId);
                    } catch (Exception e) {
                        state = State.FRAME_ERROR;
                    }

                    int bytesRead = headerBytes - headerBlock.remaining();
                    buffer.position(buffer.position() + bytesRead);
                    length -= bytesRead;
                    break;

                case DISCARD_FRAME:
                    int numBytes = Math.min(buffer.remaining(), length);
                    buffer.position(buffer.position() + numBytes);
                    length -= numBytes;
                    if (length == 0) {
                        state = State.READ_COMMON_HEADER;
                        break;
                    }
                    return;

                case FRAME_ERROR:
                    buffer.position(buffer.limit());
                    return;

                default:
                    throw new Error("Shouldn't reach here.");
            }
        }
    }

    private static boolean hasFlag(byte flags, byte flag) {
        return (flags & flag) != 0;
    }

    private static State getNextState(int type, int length) {
        switch (type) {
            case SPDY_DATA_FRAME:
                return State.READ_DATA_FRAME;

            case SPDY_SYN_STREAM_FRAME:
                return State.READ_SYN_STREAM_FRAME;

            case SPDY_SYN_REPLY_FRAME:
                return State.READ_SYN_REPLY_FRAME;

            case SPDY_RST_STREAM_FRAME:
                return State.READ_RST_STREAM_FRAME;

            case SPDY_SETTINGS_FRAME:
                return State.READ_SETTINGS_FRAME;

            case SPDY_PING_FRAME:
                return State.READ_PING_FRAME;

            case SPDY_GOAWAY_FRAME:
                return State.READ_GOAWAY_FRAME;

            case SPDY_HEADERS_FRAME:
                return State.READ_HEADERS_FRAME;

            case SPDY_WINDOW_UPDATE_FRAME:
                return State.READ_WINDOW_UPDATE_FRAME;

            default:
                if (length != 0) {
                    return State.DISCARD_FRAME;
                } else {
                    return State.READ_COMMON_HEADER;
                }
        }
    }

    private static boolean isValidFrameHeader(int streamId, int type, byte flags, int length) {
        switch (type) {
            case SPDY_DATA_FRAME:
                return streamId != 0;

            case SPDY_SYN_STREAM_FRAME:
                return length >= 10;

            case SPDY_SYN_REPLY_FRAME:
                return length >= 4;

            case SPDY_RST_STREAM_FRAME:
                return flags == 0 && length == 8;

            case SPDY_SETTINGS_FRAME:
                return length >= 4;

            case SPDY_PING_FRAME:
                return length == 4;

            case SPDY_GOAWAY_FRAME:
                return length == 8;

            case SPDY_HEADERS_FRAME:
                return length >= 4;

            case SPDY_WINDOW_UPDATE_FRAME:
                return length == 8;

            default:
                return true;
        }
    }
}
