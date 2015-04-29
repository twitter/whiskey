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

package com.twitter.whiskey.net;

import java.nio.ByteBuffer;
import static com.twitter.whiskey.net.SpdyCodecUtil.*;

public class SpdyHeaderBlockRawDecoder extends SpdyHeaderBlockDecoder {

    private static final int LENGTH_FIELD_SIZE = 4;

    private final int maxHeaderSize;

    private SpdyFrameDecoderDelegate delegate;
    private State state;

    private int headerSize;
    private int numHeaders;
    private int length;
    private String name;
    private String errorMessage;

    private enum State {
        READ_NUM_HEADERS,
        READ_NAME_LENGTH,
        READ_NAME,
        SKIP_NAME,
        READ_VALUE_LENGTH,
        READ_VALUE,
        SKIP_VALUE,
        END_HEADER_BLOCK,
        ERROR
    }

    public SpdyHeaderBlockRawDecoder(SpdyVersion spdyVersion, SpdyFrameDecoderDelegate delegate, int maxHeaderSize) {
        if (spdyVersion == null) {
            throw new NullPointerException("spdyVersion");
        }
        this.delegate = delegate;
        this.maxHeaderSize = maxHeaderSize;
        state = State.READ_NUM_HEADERS;
    }

    private static int readLengthField(ByteBuffer buffer) {
        return getSignedInt(buffer);
    }

    @Override
    void decode(ByteBuffer headerBlock, int streamId) throws Exception {
        int skipLength;
        while (headerBlock.hasRemaining()) {
            switch(state) {
                case READ_NUM_HEADERS:
                    if (headerBlock.remaining() < LENGTH_FIELD_SIZE) {
                        return;
                    }

                    numHeaders = readLengthField(headerBlock);

                    if (numHeaders < 0) {
                        errorMessage = "received header block with negative header count";
                        state = State.ERROR;
                    } else if (numHeaders == 0) {
                        state = State.END_HEADER_BLOCK;
                    } else {
                        state = State.READ_NAME_LENGTH;
                    }
                    break;

                case READ_NAME_LENGTH:
                    if (headerBlock.remaining() < LENGTH_FIELD_SIZE) {
                        return;
                    }

                    length = readLengthField(headerBlock);

                    // Recipients of a zero-length name must issue a stream error
                    if (length < 0) {
                        errorMessage = "received header with negative-length name";
                        state = State.ERROR;
                    } else if (length == 0) {
                        // TODO: skip frame and issue stream error (MS)
                        errorMessage = "received header with zero-length name";
                        state = State.ERROR;
                    } else if (length > maxHeaderSize || headerSize > maxHeaderSize - length) {
                        headerSize = maxHeaderSize + 1;
                        errorMessage = "header name length is too long";
                        state = State.SKIP_NAME;
                    } else {
                        headerSize += length;
                        state = State.READ_NAME;
                    }
                    break;

                case READ_NAME:
                    if (headerBlock.remaining() < length) {
                        return;
                    }

                    byte[] nameBytes = new byte[length];
                    headerBlock.get(nameBytes);
                    name = new String(nameBytes, "UTF-8");
                    state = State.READ_VALUE_LENGTH;
                    break;

                case SKIP_NAME:
                    skipLength = Math.min(headerBlock.remaining(), length);
                    headerBlock.position(headerBlock.position() + skipLength);
                    length -= skipLength;

                    if (length == 0) {
                        state = State.READ_VALUE_LENGTH;
                    }
                    break;

                case READ_VALUE_LENGTH:
                    if (headerBlock.remaining() < LENGTH_FIELD_SIZE) {
                        return;
                    }

                    length = readLengthField(headerBlock);

                    // Recipients of illegal value fields must issue a stream error
                    if (length < 0) {
                        errorMessage = "received header with negative-length value";
                        state = State.ERROR;
                    } else if (length == 0) {
                        // SPDY/3 allows zero-length (empty) header values
                        delegate.readHeader(streamId, new Header(name, ""));

                        name = null;
                        if (--numHeaders == 0) {
                            state = State.END_HEADER_BLOCK;
                        } else {
                            state = State.READ_NAME_LENGTH;
                        }

                    } else if (length > maxHeaderSize || headerSize > maxHeaderSize - length) {
                        headerSize = maxHeaderSize + 1;
                        name = null;
                        state = State.SKIP_VALUE;
                    } else {
                        headerSize += length;
                        state = State.READ_VALUE;
                    }
                    break;

                case READ_VALUE:
                    if (headerBlock.remaining() < length) {
                        return;
                    }

                    byte[] valueBytes = new byte[length];
                    headerBlock.get(valueBytes);

                    // Add Name/Value pair to headers
                    int index = 0;
                    int offset = 0;

                    // Value must not start with a NULL character
                    if (valueBytes[0] == (byte) 0) {
                        state = State.ERROR;
                        break;
                    }

                    while (index < length) {
                        while (index < valueBytes.length && valueBytes[index] != (byte) 0) {
                            index ++;
                        }
                        if (index < valueBytes.length) {
                            // Received NULL character
                            if (index + 1 == valueBytes.length || valueBytes[index + 1] == (byte) 0) {
                                // Value field ended with a NULL character or
                                // received multiple, in-sequence NULL characters.
                                // Recipients of illegal value fields must issue a stream error
                                errorMessage = "received header with invalid value";
                                state = State.ERROR;
                                break;
                            }
                        }
                        String value = new String(valueBytes, offset, index - offset, "UTF-8");

                        delegate.readHeader(streamId, new Header(name, value));
                        // TODO: Handle name that contains NULL or non-ascii characters

                        index ++;
                        offset = index;
                    }

                    name = null;

                    // If we broke out of the add header loop, break here
                    if (state == State.ERROR) {
                        break;
                    }

                    if (--numHeaders == 0) {
                        state = State.END_HEADER_BLOCK;
                    } else {
                        state = State.READ_NAME_LENGTH;
                    }
                    break;

                case SKIP_VALUE:
                    skipLength = Math.min(headerBlock.remaining(), length);
                    headerBlock.position(headerBlock.position() + skipLength);
                    length -= skipLength;

                    if (length == 0) {
                        if (--numHeaders == 0) {
                            state = State.END_HEADER_BLOCK;
                        } else {
                            state = State.READ_NAME_LENGTH;
                        }
                    }
                    break;

                case END_HEADER_BLOCK:
                    errorMessage = "unexpected end of header block";
                    state = State.ERROR;
                    break;

                case ERROR:
                    headerBlock.position(headerBlock.limit());
                    delegate.readFrameError(errorMessage);
                    return;

                default:
                    throw new Error("Shouldn't reach here.");
            }
        }
    }

    @Override
    void endHeaderBlock() {
        if (state != State.END_HEADER_BLOCK) {
            delegate.readFrameError("unexpected end of header block");
        }

        // Initialize header block decoding fields
        headerSize = 0;
        name = null;
        state = State.READ_NUM_HEADERS;
    }

    @Override
    void end() {
    }
}
