package com.twitter.internal.network.whiskey;

import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * @author Michael Schore
 */
final class SpdyHeaderBlockZlibDecoder extends SpdyHeaderBlockRawDecoder {

    private static final int DEFAULT_BUFFER_CAPACITY = 16386;
    private static final SpdyProtocolException INVALID_HEADER_BLOCK =
        new SpdyProtocolException("Invalid Header Block");

    private final Inflater decompressor = new Inflater();

    private ByteBuffer decompressed;

    SpdyHeaderBlockZlibDecoder(SpdyVersion spdyVersion, SpdyFrameDecoderDelegate delegate, int maxHeaderSize) {
        super(spdyVersion, delegate, maxHeaderSize);
        decompressed = ByteBuffer.allocate(DEFAULT_BUFFER_CAPACITY);
    }

    @Override
    void decode(ByteBuffer headerBlock, int streamId) throws Exception {
        int len = setInput(headerBlock);

        int numBytes;
        int totalBytes = 0;
        do {
            numBytes = decompress(streamId);
            totalBytes += numBytes;
        } while (numBytes > 0);

        // z_stream has an internal 64-bit hold buffer
        // it is always capable of consuming the entire input
        if (decompressor.getRemaining() != 0 /*|| totalBytes != len*/) {
            // we reached the end of the deflate stream
            throw INVALID_HEADER_BLOCK;
        }

        headerBlock.position(headerBlock.position() + len);
    }

    private int setInput(ByteBuffer compressed) {
        int len = compressed.remaining();

        if (compressed.hasArray()) {
            decompressor.setInput(compressed.array(), compressed.arrayOffset() + compressed.position(), len);
        } else {
            byte[] in = new byte[len];
            compressed.get(in);
            decompressor.setInput(in, 0, in.length);
        }

        return len;
    }

    private int decompress(int streamId) throws Exception {

        byte[] out = decompressed.array();
        int offset = decompressed.arrayOffset() + decompressed.position();
        try {
            int numBytes = decompressor.inflate(out, offset, decompressed.remaining());
            if (numBytes == 0 && decompressor.needsDictionary()) {
                try {
                    decompressor.setDictionary(SpdyCodecUtil.SPDY_DICT);
                } catch (IllegalArgumentException ignored) {
                    throw INVALID_HEADER_BLOCK;
                }
                numBytes = decompressor.inflate(out, offset, decompressed.remaining());
            }

            decompressed.position(decompressed.position() + numBytes);
            decompressed.flip();
            super.decode(decompressed, streamId);
            decompressed.compact();

            return numBytes;
        } catch (DataFormatException e) {
            throw new SpdyProtocolException("Received invalid header block", e);
        }
    }

    @Override
    void endHeaderBlock() {
        super.endHeaderBlock();
        releaseBuffer();
    }

    @Override
    public void end() {
        super.end();
        releaseBuffer();
        decompressor.end();
    }

    private void releaseBuffer() {
    }
}
