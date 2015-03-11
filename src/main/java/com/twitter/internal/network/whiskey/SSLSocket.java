package com.twitter.internal.network.whiskey;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executor;

final class SSLSocket extends Socket {

    private static final ByteBuffer[] EMPTY_BUFFER_ARRAY = new ByteBuffer[0];

    private final SSLEngine engine;

    private Deque<WriteFuture> handshakeWriteQueue = new ArrayDeque<>(32);

    SSLSocket(Origin origin, RunLoop runLoop, SSLEngine engine) {
        super(origin, runLoop);
        this.engine = engine;
        this.engine.setUseClientMode(true);
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

    private List<ByteBuffer> wrap(ByteBuffer[] buffer) throws SSLException {
        ArrayList<ByteBuffer> ret = new ArrayList<>();

        while (true) {
            ByteBuffer out = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());

            SSLEngineResult result = engine.wrap(buffer, out);

            if (out.remaining() > 0) {
                out.flip();
                ret.add(out);
            }

            switch (result.getHandshakeStatus()) {
                case FINISHED:
                    super.finishConnect();
                    return ret;
                case NEED_TASK:
                    runDelegatedTasks(engine);
                    break;
                case NEED_UNWRAP:
                    readAndUnwrapHandshake();
                    return ret;
                case NEED_WRAP:
                case NOT_HANDSHAKING:
                    break;
            }

            if (result.bytesProduced() == 0) {
                break;
            }
        }

        return ret;
    }

    private void wrapHandshake() throws SSLException {
        List<ByteBuffer> buffers = wrap(EMPTY_BUFFER_ARRAY);
        if (!buffers.isEmpty()) {
            ByteBuffer[] bb = new ByteBuffer[buffers.size()];
            buffers.toArray(bb);

            WriteFuture f = new WriteFuture(bb);
            handshakeWriteQueue.add(f);
        }
    }

    private void unwrapHandshake(ByteBuffer wrappedBuf) throws SSLException {

        while (true) {
            ByteBuffer unwrappedBuf = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());

            SSLEngineResult result = engine.unwrap(wrappedBuf, unwrappedBuf);

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

    private List<ByteBuffer> unwrap(ByteBuffer wrappedBuf) throws SSLException {
        ArrayList<ByteBuffer> ret = new ArrayList<>();

        while (true) {
            ByteBuffer unwrappedBuf = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());

            SSLEngineResult result = engine.unwrap(wrappedBuf, unwrappedBuf);

            unwrappedBuf.flip();

            if (unwrappedBuf.hasRemaining()) {
                ret.add(unwrappedBuf);
            }

            if (result.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                throw new SSLException("renegotiation not supported");
            }

            if (result.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                break;
            }
        }

        return ret;
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
                return InlineExecutor.instance();
            }

        });
    }

    @Override
    ReadFuture read() {
        final ReadFuture readFuture = new ReadFuture();

        ReadFuture rawReadFuture = super.read();
        rawReadFuture.addListener(new Listener<ByteBuffer>() {

            @Override
            public void onComplete(ByteBuffer result) {

                List<ByteBuffer> unwrapped;
                try {
                    unwrapped = unwrap(result);
                } catch (SSLException ssle) {
                    readFuture.fail(ssle);
                    return;
                }

                // TODO(bgallagher)
                readFuture.set(unwrapped.get(0));
            }

            @Override
            public void onError(Throwable throwable) {
                readFuture.fail(throwable);

            }

            @Override
            public Executor getExecutor() {
                return InlineExecutor.instance();
            }
        });

        return readFuture;
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
    void writeInternal(WriteFuture writeFuture) {
        List<ByteBuffer> l;

        try {
            l = wrap(writeFuture.pending());
        } catch (SSLException e) {
            writeFuture.fail(e);
            return;
        }

        ByteBuffer[] bb = new ByteBuffer[l.size()];
        l.toArray(bb);
        writeFuture.setPending(bb);
        super.writeInternal(writeFuture);
    }

    private static void runDelegatedTasks(SSLEngine engine) {
        Runnable task;
        while ((task = engine.getDelegatedTask()) != null) {
            task.run();
        }
    }

}
