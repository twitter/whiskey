package com.twitter.internal.network.whiskey;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.concurrent.TimeUnit;


class Socket implements SelectableSocket {

    private static final int SOCKET_BUFFER_SIZE = 65536;

    private final Origin origin;
    private final RunLoop runLoop;
    private final ByteBuffer socketBuffer;

    private boolean closed = false;

    private SocketChannel channel;

    private ConnectFuture connectFuture;
    private Deque<ReadFuture> readQueue = new ArrayDeque<>(1);
    private Deque<WriteFuture> writeQueue = new ArrayDeque<>(32);

    Socket(Origin origin, RunLoop runLoop) {
        this.origin = origin;
        this.runLoop = runLoop;
        socketBuffer = ByteBuffer.allocate(SOCKET_BUFFER_SIZE);
    }

    ConnectFuture connect() {
        connectFuture = new ConnectFuture();

        runLoop.execute(new Runnable() {
            public void run() {
                try {
                    channel = SocketChannel.open();
                    channel.configureBlocking(false);
                    channel.connect(new InetSocketAddress(origin.getHost(), origin.getPort()));
                    reregister();
                } catch (IOException e) {
                    connectFuture.fail(e);
                    closed = true;
                }
            }
        });

        return connectFuture;
    }

    ReadFuture read() {
        final ReadFuture readFuture = new ReadFuture();

        runLoop.execute(new Runnable() {
            public void run() {
                readQueue.add(readFuture);

                if (isConnected() && readQueue.size() == 1) {
                    reregister();
                }
            }
        });

        return readFuture;
    }

    ReadFuture read(int timeout, TimeUnit timeoutUnit) {
        return read();
    }

    WriteFuture write(ByteBuffer data) {
        return write(new ByteBuffer[]{data});
    }

    WriteFuture write(ByteBuffer[] data) {
        final WriteFuture writeFuture = new WriteFuture(data);

        runLoop.execute(new Runnable() {
            public void run() {
                writeInternal(writeFuture);
            }
        });

        return writeFuture;
    }

    WriteFuture write(ByteBuffer data, int timeout, TimeUnit timeoutUnit) {
        return write(new ByteBuffer[]{data});
    }

    WriteFuture write(ByteBuffer[] data, int timeout, TimeUnit timeoutUnit) {
        return write(data);
    }

    void writeInternal(WriteFuture writeFuture) {
        getWriteQueue().add(writeFuture);

        if (isConnected() && getWriteQueue().size() == 1) {
            reregister();
        }
    }

    Deque<WriteFuture> getWriteQueue() {
        return writeQueue;
    }

    @Override
    public void onConnect() {
        try {
            channel.finishConnect();
            finishConnect();
        } catch (IOException e) {
            connectFuture.fail(e);
            closed = true;
        }
    }

    void finishConnect() {
        connectFuture.set(origin);
        reregister();
    }

    void failConnect(Throwable thr) {
        connectFuture.fail(thr);
    }

    @Override
    public void onReadable() {

        if (closed) {
            return;
        }

        if (readQueue.isEmpty()) {
            reregister();
            return;
        }

        int bytesRead;
        try {
            bytesRead = channel.read(socketBuffer);
        } catch (IOException e) {
            close(e);
            return;
        }

        // Shouldn't be possible if both headers and body data can be incrementally decoded
        assert(bytesRead > 0 || socketBuffer.position() < socketBuffer.limit());

        ReadFuture currentRead = readQueue.peek();

        assert(!currentRead.isDone());

        if (bytesRead > 0) {
            socketBuffer.flip();
            currentRead.set(socketBuffer);
            socketBuffer.compact();
            readQueue.poll();
        }

        reregister();
    }

    @Override
    public void onWriteable() {

        if (closed) {
            return;
        }

        if (getWriteQueue().isEmpty()) {
            reregister();
            return;
        }

        WriteFuture currentWrite = getWriteQueue().peek();

        assert(!currentWrite.isDone());
        ByteBuffer[] writeData = currentWrite.pending();

        long bytesWritten;
        try {
             bytesWritten = channel.write(writeData);
        } catch (IOException e) {
            close(e);
            return;
        }

        ByteBuffer finalData = writeData[writeData.length - 1];
        boolean writeComplete = finalData.position() == finalData.limit();
        currentWrite.provide(bytesWritten, writeComplete);
        if (writeComplete) {
            getWriteQueue().poll();
        }

        reregister();
    }

    void reregister() {
        runLoop.register(interestSet(), this);
    }

    @Override
    public SocketChannel getChannel() {
        return channel;
    }

    public boolean isConnected() {
        return channel.isConnected();
    }

    private int interestSet() {
        if (channel.isConnectionPending()) return SelectionKey.OP_CONNECT;

        int interestSet = 0;
        if (!readQueue.isEmpty()) interestSet = SelectionKey.OP_READ;
        if (!getWriteQueue().isEmpty()) interestSet |= SelectionKey.OP_WRITE;
        return interestSet;
    }

    @Override
    public void onClose() {
        close();
    }

    boolean isSecure() {
        return false;
    }

    void close(Throwable e) {
        if (closed) return;
        closed = true;
    }

    void close() {
        if (closed) return;
        closed = true;
    }

    public Request.Protocol getProtocol() {
        return Request.Protocol.SPDY_3_1;
    }

    class ConnectFuture extends CompletableFuture<Origin> {
    }

    class ReadFuture extends CompletableFuture<ByteBuffer> {

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return readQueue.contains(this) && super.cancel(mayInterruptIfRunning) && readQueue.remove(this);
        }
    }

    class WriteFuture extends ReactiveFuture<Long, Long> {
        ByteBuffer[] data;
        ArrayList<Long> bytesWritten;
        Long totalBytesWritten;
        private ByteBuffer[] pending;

        WriteFuture(ByteBuffer[] data) {
            this.data = data;
            bytesWritten = new ArrayList<>();
            totalBytesWritten = (long)0;
        }

        ByteBuffer[] pending() {
            return data;
        }

        public void setPending(ByteBuffer[] pending) {
            this.data = pending;
        }

        @Override
        void accumulate(Long element) {
            bytesWritten.add(element);
            totalBytesWritten += element;
        }

        @Override
        Iterable<Long> drain() {
            return bytesWritten;
        }

        @Override
        void complete() {
            set(totalBytesWritten);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return getWriteQueue().contains(this) && super.cancel(mayInterruptIfRunning) && getWriteQueue().remove(this);
        }

    }
}
