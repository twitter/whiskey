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

package com.twitter.whiskey.util;

import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
* Inflater implementation that directly supports most common formats. Adapted from netty's
* JdkZlibDecoder.
*
* @author Michael Schore
*/
public class ZlibInflater extends Inflater {

    private static final int FHCRC = 0x02;
    private static final int FEXTRA = 0x04;
    private static final int FNAME = 0x08;
    private static final int FCOMMENT = 0x10;
    private static final int FRESERVED = 0xE0;

    private ByteBuffer in;
    private ByteBuffer accumulator;
    private Inflater inflater;
    private final byte[] dictionary;

    // GZIP related
    private final CRC32 crc;

    public enum Wrapper {
        GZIP,
        ZLIB,
        NONE,
        UNKNOWN
    }

    private enum GzipState {
        HEADER_START,
        HEADER_END,
        FLG_READ,
        XLEN_READ,
        SKIP_FNAME,
        SKIP_COMMENT,
        PROCESS_FHCRC,
        FOOTER_START,
        FOOTER_END
    }

    private GzipState gzipState = GzipState.HEADER_START;
    private int flags = -1;
    private int xlen = -1;

    private volatile boolean finished;

    private boolean determineWrapper;

    /**
     * Creates a new instance with the default wrapper ({@link com.twitter.whiskey.ZlibInflater.Wrapper#ZLIB}).
     */
    public ZlibInflater() {
        this(Wrapper.ZLIB, null);
    }

    /**
     * Creates a new instance with the specified preset dictionary. The wrapper
     * is always {@link com.twitter.whiskey.ZlibInflater.Wrapper#ZLIB} because it is the only format that
     * supports the preset dictionary.
     */
    public ZlibInflater(byte[] dictionary) {
        this(Wrapper.ZLIB, dictionary);
    }

    /**
     * Creates a new instance with the specified wrapper.
     * Be aware that only {@link com.twitter.whiskey.ZlibInflater.Wrapper#GZIP}, {@link com.twitter.whiskey.ZlibInflater.Wrapper#ZLIB} and {@link com.twitter.whiskey.ZlibInflater.Wrapper#NONE} are
     * supported atm.
     */
    public ZlibInflater(Wrapper wrapper) {
        this(wrapper, null);
    }

    private ZlibInflater(Wrapper wrapper, byte[] dictionary) {
        if (wrapper == null) {
            throw new NullPointerException("wrapper");
        }
        switch (wrapper) {
            case GZIP:
                inflater = new Inflater(true);
                crc = new CRC32();
                // TODO: determine necessary buffer size and growth parameters
                accumulator = ByteBuffer.allocate(256);
                accumulator.flip();
                break;
            case NONE:
                inflater = new Inflater(true);
                crc = null;
                break;
            case ZLIB:
                inflater = new Inflater();
                crc = null;
                break;
            case UNKNOWN:
                // Postpone the decision until setInput(...) is called.
                determineWrapper = true;
                crc = null;
                break;
            default:
                throw new IllegalArgumentException("Only GZIP or ZLIB is supported, but you used " + wrapper);
        }
        this.dictionary = dictionary;
    }

    @Override
    public void setInput(byte[] b, int off, int len) {
        in = ByteBuffer.wrap(b, off, len);
    }

    @Override
    public void setDictionary(byte[] b, int off, int len) {
        inflater.setDictionary(b, off, len);
    }

    @Override
    public int getRemaining() {
        if (inflater == null) return 0;
        return inflater.getRemaining() - (gzipState == GzipState.FOOTER_END ? 8 : 0);
    }

    @Override
    public boolean needsInput() {
        return in == null || !in.hasRemaining() && (inflater == null || inflater.needsInput());
    }

    @Override
    public boolean needsDictionary() {
        return inflater.needsDictionary();
    }

    @Override
    public boolean finished() {
        return finished;
    }

    @Override
    public int getAdler() {
        return inflater.getAdler();
    }

    @Override
    public long getBytesRead() {
        return inflater.getBytesRead();
    }

    @Override
    public long getBytesWritten() {
        return inflater.getBytesWritten();
    }

    @Override
    public void reset() {
        inflater.reset();
    }

    @Override
    public void end() {
        inflater.end();
    }

    @Override
    protected void finalize() {
        super.finalize();
        if (inflater != null) inflater.end();
    }

    @Override
    public int inflate(byte[] b, int off, int len) throws DataFormatException {
        if (finished) {
            if (in.hasRemaining()) throw new DataFormatException("zlib stream ended unexpectedly");
            return 0;
        }

        if (!in.hasRemaining()) return 0;

        if (determineWrapper) {
            // First two bytes are needed to decide if it's a ZLIB stream.
            if (accumulator.remaining() + len < 2) {
                buffer();
                return 0;
            }

            byte[] cmf_flg = new byte[2];
            cmf_flg[0] = readByte();
            cmf_flg[1] = readByte();
            boolean nowrap = !looksLikeZlib(cmf_flg[0], cmf_flg[1]);
            inflater = new Inflater(nowrap);
            inflater.inflate(cmf_flg, 0, 2);
            determineWrapper = false;
        }


        if (crc != null) {
            switch (gzipState) {
                case FOOTER_START:
                    if (readGZIPFooter()) {
                        finished = true;
                    }
                    return 0;
                default:
                    if (gzipState != GzipState.HEADER_END) {
                        if (!readGZIPHeader()) {
                            return 0;
                        }
                    }
            }
            // Some bytes may have been consumed, and so we must re-set the number of readable bytes.
//            readableBytes = in.readableBytes();
        }

        inflater.setInput(in.array(), in.arrayOffset() + in.position(), in.remaining());

        boolean readFooter = false;
        int totalWritten = 0;
        while (off < len && !inflater.needsInput()) {

            int bytesWritten = inflater.inflate(b, off, len);
            if (bytesWritten > 0) {
                totalWritten += bytesWritten;
                if (crc != null) {
                    crc.update(b, off, bytesWritten);
                }
                off += bytesWritten;
            } else {
                if (inflater.needsDictionary()) {
                    inflater.setDictionary(dictionary);
                }
            }

            if (inflater.finished()) {
                if (crc == null) {
                    finished = true; // Do not decode anymore.
                } else {
                    readFooter = true;
                }
                break;
            }
        }

        in.position(in.position() + in.remaining() - inflater.getRemaining());

        if (readFooter) {
            gzipState = GzipState.FOOTER_START;
            if (readGZIPFooter()) {
                finished = true;
            }
        }

        return totalWritten;
    }

    private byte readByte() {
        return accumulator.hasRemaining() ? accumulator.get() : in.get();
    }

    public short readUnsignedByte() {
        return (short) (readByte() & 0xFF);
    }

    private void readBytes(byte[] array) {
        int accumulated = accumulator.remaining();
        accumulator.get(array, 0, accumulated);
        in.get(array, accumulated, in.remaining());
    }

    private short readShort() {
        byte high = readByte();
        byte low = readByte();
        return (short) (high << 8 | low);
    }

    private int bytesRemaining() {
        return accumulator.remaining() + in.remaining();
    }

    private void buffer() {
        accumulator.compact();
        accumulator.put(in);
        accumulator.flip();
    }

    private boolean readGZIPHeader() throws DataFormatException {
        switch (gzipState) {
            case HEADER_START:
                if (bytesRemaining() < 10) {
                    buffer();
                    return false;
                }
                // read magic numbers
                int magic0 = readByte();
                int magic1 = readByte();

                if (magic0 != 31) {
                    throw new DataFormatException("Input is not in the GZIP format");
                }
                crc.update(magic0);
                crc.update(magic1);

                int method = readUnsignedByte();
                if (method != Deflater.DEFLATED) {
                    throw new DataFormatException("Unsupported compression method "
                        + method + " in the GZIP header");
                }
                crc.update(method);

                flags = readUnsignedByte();
                crc.update(flags);

                if ((flags & FRESERVED) != 0) {
                    throw new DataFormatException(
                        "Reserved flags are set in the GZIP header");
                }

                // mtime (int)
                crc.update(readByte());
                crc.update(readByte());
                crc.update(readByte());
                crc.update(readByte());

                crc.update(readUnsignedByte()); // extra flags
                crc.update(readUnsignedByte()); // operating system

                gzipState = GzipState.FLG_READ;
            case FLG_READ:
                if ((flags & FEXTRA) != 0) {
                    if (bytesRemaining() < 2) {
                        buffer();
                        return false;
                    }
                    int xlen1 = readUnsignedByte();
                    int xlen2 = readUnsignedByte();
                    crc.update(xlen1);
                    crc.update(xlen2);

                    xlen |= xlen1 << 8 | xlen2;
                }
                gzipState = GzipState.XLEN_READ;
            case XLEN_READ:
                if (xlen != -1) {
                    if (bytesRemaining() < xlen) {
                        buffer();
                        return false;
                    }
                    byte[] xtra = new byte[xlen];
                    readBytes(xtra);
                    crc.update(xtra);
                }
                gzipState = GzipState.SKIP_FNAME;
            case SKIP_FNAME:
                if ((flags & FNAME) != 0) {
                    while (bytesRemaining() > 0) {
                        int b = readUnsignedByte();
                        crc.update(b);
                        if (b == 0x00) {
                            gzipState = GzipState.SKIP_COMMENT;
                            break;
                        }
                    }
                    // No buffering required, since above loop consumes all available bytes
                    if (gzipState == GzipState.SKIP_FNAME) return false;
                }
            case SKIP_COMMENT:
                if ((flags & FCOMMENT) != 0) {
                    while (bytesRemaining() > 0) {
                        int b = readUnsignedByte();
                        crc.update(b);
                        if (b == 0x00) {
                            break;
                        }
                    }
                    // No buffering required, since above loop consumes all available bytes
                    if (gzipState == GzipState.SKIP_COMMENT) return false;
                }
                gzipState = GzipState.PROCESS_FHCRC;
            case PROCESS_FHCRC:
                if ((flags & FHCRC) != 0) {
                    if (bytesRemaining() < 4) {
                        buffer();
                        return false;
                    }
                    verifyCrc();
                }
                crc.reset();
                gzipState = GzipState.HEADER_END;
            case HEADER_END:
                return true;
            default:
                throw new IllegalStateException();
        }
    }

    private boolean readGZIPFooter() throws DataFormatException {
        if (bytesRemaining() < 8) {
            buffer();
            return false;
        }

        verifyCrc();

        // read ISIZE and verify
        int dataLength = 0;
        for (int i = 0; i < 4; ++i) {
            dataLength |= readUnsignedByte() << i * 8;
        }
        int readLength = inflater.getTotalOut();
        if (dataLength != readLength) {
            throw new DataFormatException(
                "Number of bytes mismatch. Expected: " + dataLength + ", Got: " + readLength);
        }
        gzipState = GzipState.FOOTER_END;
        return true;
    }

    private void verifyCrc() throws DataFormatException {
        long crcValue = 0;
        for (int i = 0; i < 4; ++i) {
            crcValue |= (long) readUnsignedByte() << i * 8;
        }
        long readCrc = crc.getValue();
        if (crcValue != readCrc) {
            throw new DataFormatException(
                "CRC value missmatch. Expected: " + crcValue + ", Got: " + readCrc);
        }
    }

    /*
     * Returns true if the cmf_flg parameter (think: first two bytes of a zlib stream)
     * indicates that this is a zlib stream.
     * <p>
     * You can lookup the details in the ZLIB RFC:
     * <a href="http://tools.ietf.org/html/rfc1950#section-2.2">RFC 1950</a>.
     */
    private static boolean looksLikeZlib(byte cmf, byte flg) {
        short cmf_flg = (short) (cmf << 8 | flg);
        return (cmf_flg & 0x7800) == 0x7800 &&
            cmf_flg % 31 == 0;
    }
}
