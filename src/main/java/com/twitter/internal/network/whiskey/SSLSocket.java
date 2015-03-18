package com.twitter.internal.network.whiskey;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.concurrent.Executor;

final class SSLSocket extends Socket {

    private static final ByteBuffer[] EMPTY_BUFFER_ARRAY = new ByteBuffer[0];

    private final SSLEngine engine;

    private final Deque<WriteFuture> handshakeWriteQueue = new ArrayDeque<>(32);
    private final Deque<ReadFuture> handshakeReadQueue = new ArrayDeque<>();

    private final ByteBuffer bufferedWrapped;

    SSLSocket(Origin origin, RunLoop runLoop, SSLEngine engine) {
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
    void finishConnect() {
        // writing an empty buffer will initiate a handshake
        try {
            wrapHandshake();
        } catch (SSLException ssle) {
            failConnect(ssle);
        }
    }

    private void wrapHandshake() throws SSLException {
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

    private void unwrapHandshake(ByteBuffer wrappedBuf) throws SSLException {

        while (true) {
            // TODO(bgallagher) buffer pooling
            ByteBuffer to = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());

            bufferedWrapped.put(wrappedBuf);
            bufferedWrapped.flip();

            SSLEngineResult result = engine.unwrap(bufferedWrapped, to);

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

            if (result.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                readAndUnwrapHandshake();
                break;
            }
        }
    }

    private void readAndUnwrapHandshake() {
        ReadFuture readFuture = super.read();
        readFuture.addListener(new Listener<ByteBuffer>() {

            @Override
            public void onComplete(ByteBuffer result) {
                try {
                    unwrapHandshake(result);
                } catch (SSLException ssle) {
                    failConnect(ssle);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                failConnect(throwable);
            }

            @Override
            public Executor getExecutor() {
                return Inline.INSTANCE;
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
    ReadFuture read() {
        return read(new SSLReadFuture());
    }

    @Override
    WriteFuture write(ByteBuffer[] data) {
        return write(new SSLWriteFuture(data));
    }

    @Override
    Deque<ReadFuture> getReadQueue() {
        if (engine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            return super.getReadQueue();
        } else {
            return handshakeReadQueue;
        }
    }
    @Override
    Deque<WriteFuture> getWriteQueue() {
        if (engine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            return super.getWriteQueue();
        } else {
            return handshakeWriteQueue;
        }
    }

    @Override
    void close() {
        engine.closeOutbound();
        super.close();
    }

    private final class SSLReadFuture extends ReadFuture {

        @Override
        boolean doRead(SocketChannel channel) throws IOException {

            ByteBuffer out = getBuffer();

            channel.read(bufferedWrapped);
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
