/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.nio;

import com.twitter.whiskey.futures.Inline;
import com.twitter.whiskey.util.Origin;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;

/**
 * An asynchronous TLS socket interface.
 *
 * @author Bill Gallagher
 */
public final class SSLSocket extends Socket {

    private static final ByteBuffer[] EMPTY_BUFFER_ARRAY = new ByteBuffer[0];

    private final SSLEngine engine;

    private final Deque<WriteFuture> handshakeWriteQueue = new ArrayDeque<>(32);
    private final Deque<ReadFuture> handshakeReadQueue = new ArrayDeque<>();

    private final ByteBuffer bufferedWrapped;

    public SSLSocket(Origin origin, RunLoop runLoop, SSLEngine engine) {
        super(origin, runLoop);
        this.engine = engine;
        this.engine.setUseClientMode(true);
        bufferedWrapped = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
    }

    @Override
    boolean isSecure() {
        return true;
    }

    @Override
    void finishConnect() throws IOException {
        // writing an empty buffer will initiate a handshake
        wrapHandshake();
    }

    private void wrapHandshake() throws IOException {
        ByteBuffer out = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());

        SSLEngineResult result;
        do {
            result = engine.wrap(EMPTY_BUFFER_ARRAY, out);

            if (result.bytesProduced() > 0) {
                out.flip();
                handshakeWriteQueue.add(new WriteFuture(new ByteBuffer[] { out }));
                out = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
            }

            switch (result.getHandshakeStatus()) {
                case FINISHED:
                    super.finishConnect();
                    break;
                case NEED_TASK:
                    runDelegatedTasks(engine);
                    break;
                case NEED_UNWRAP:
                    readAndUnwrapHandshake();
                    break;
                case NEED_WRAP:
                case NOT_HANDSHAKING:
                    break;
            }
        } while (result.bytesProduced() > 0);
    }

    private void unwrapHandshake(ByteBuffer wrappedBuf) throws IOException {

        SSLEngineResult result;
        do {
            // TODO(bgallagher) buffer pooling
            ByteBuffer to = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());

            bufferedWrapped.put(wrappedBuf);
            bufferedWrapped.flip();
            result = engine.unwrap(bufferedWrapped, to);
            bufferedWrapped.compact();

            switch (result.getHandshakeStatus()) {
                case NEED_UNWRAP:
                    break;
                case NEED_WRAP:
                    wrapHandshake();
                    return;
                case NEED_TASK:
                    runDelegatedTasks(engine);
                    break;
                case FINISHED:
                    super.finishConnect();
                    if (bufferedWrapped.position() > 0) {
                        onReadable();
                    }
                    return;
                case NOT_HANDSHAKING:
                    break;
            }
        } while (result.getStatus() != SSLEngineResult.Status.BUFFER_UNDERFLOW);

        readAndUnwrapHandshake();
    }

    private void readAndUnwrapHandshake() {
        ReadFuture readFuture = super.read();
        readFuture.addListener(new Inline.Listener<ByteBuffer>() {

            @Override
            public void onComplete(ByteBuffer result) {
                try {
                    unwrapHandshake(result);
                } catch (IOException ioe) {
                    failConnect(ioe);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                failConnect(throwable);
            }
        });
    }

    private static void runDelegatedTasks(SSLEngine engine) {
        Runnable task;
        while ((task = engine.getDelegatedTask()) != null) {
            task.run();
        }
    }

    @Override
    public ReadFuture read(ByteBuffer readBuffer) {
        return read(new SSLReadFuture(readBuffer));
    }

    @Override
    public ReadFuture read() {
        return read(new SSLReadFuture());
    }

    @Override
    public WriteFuture write(ByteBuffer[] data) {
        return write(new SSLWriteFuture(data));
    }

    @Override
    protected Deque<ReadFuture> getReadQueue() {
        if (engine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            return super.getReadQueue();
        } else {
            return handshakeReadQueue;
        }
    }

    @Override
    protected Deque<WriteFuture> getWriteQueue() {
        if (engine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            return super.getWriteQueue();
        } else {
            return handshakeWriteQueue;
        }
    }

    @Override
    public void close() {
        engine.closeOutbound();
        super.close();
    }

    private final class SSLReadFuture extends ReadFuture {

        SSLReadFuture() {
            super();
        }

        SSLReadFuture(ByteBuffer buffer) {
            super(buffer);
        }

        @Override
        boolean doRead(SocketChannel channel) throws IOException {

            ByteBuffer out = getBuffer();

            int bytesRead = channel.read(bufferedWrapped);

            if (bytesRead < 0) {
                fail(new IOException("connection closed"));
                return true;
            }

            bufferedWrapped.flip();

            SSLEngineResult.Status status = SSLEngineResult.Status.OK;
            while (out.remaining() > 0 && bufferedWrapped.remaining() > 0 && status ==
                SSLEngineResult.Status.OK) {

                SSLEngineResult result = engine.unwrap(bufferedWrapped, out);
                status = result.getStatus();

                if (result.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                    throw new SSLException("renegotiation not supported");
                }

            }

            bufferedWrapped.compact();

            out.flip();
            set(out);
            return true;
        }
    }

    private final class SSLWriteFuture extends WriteFuture {

        private boolean wrapped = false;

        SSLWriteFuture(ByteBuffer[] data) {
            super(data);
        }

        private void wrap() throws IOException {

            ArrayList<ByteBuffer> wrapped = new ArrayList<>();

            while (true) {
                // TODO(bgallagher) buffer pooling
                ByteBuffer out = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());

                SSLEngineResult result = engine.wrap(pending(), out);

                if (result.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                    throw new SSLException("renegotiation not supported");
                }

                if (result.bytesProduced() > 0) {
                    out.flip();
                    wrapped.add(out);
                } else {
                    break;
                }
            }

            setPending(wrapped.toArray(new ByteBuffer[wrapped.size()]));
        }

        boolean doWrite() throws IOException {
            if (!wrapped) {
                wrap();
                wrapped = true;
            }

            return super.doWrite();
        }
    }
}
