package com.twitter.internal.network.whiskey;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

// TODO: remove readqueue -> currentRead only?
// TODO: create runloopsocket interface
class Socket {
    private static final int SOCKET_BUFFER_SIZE = 65536;

    private boolean disconnected = false;
    private boolean closed = false;

    private SocketChannel channel;
    private SSLContext context;
    private SSLEngine engine;

    private ByteBuffer socketBuffer;
    private ConnectFuture connectFuture;
    private ReadFuture currentRead;
    private WriteFuture currentWrite;
    private Deque<ReadFuture> readQueue = new ArrayDeque<>(1);
    private Deque<WriteFuture> writeQueue = new ArrayDeque<>(32);
    private RunLoop runLoop;
    private Origin origin;

    Socket(Origin origin, RunLoop runLoop) {
        this.origin = origin;
        this.runLoop = runLoop;
        socketBuffer = ByteBuffer.allocate(SOCKET_BUFFER_SIZE);
    }

    ConnectFuture connect() {

        connectFuture = new ConnectFuture();
        try {
            channel = SocketChannel.open();
            channel.configureBlocking(false);
            runLoop.register(this);
            channel.connect(new InetSocketAddress(origin.getHost(), origin.getPort()));
        } catch (IOException e) {
            connectFuture.fail(e);
            closed = true;
        }
        return connectFuture;
    }

    SocketChannel getChannel() {
        return channel;
    }

    public boolean isConnected() {
        return channel.isConnected();
    }

    void onConnect() {

        try {
            channel.finishConnect();
        } catch (IOException e) {
            connectFuture.fail(e);
            closed = true;
        }
        finishConnect();
    }

    void finishConnect() {

        connectFuture.set(origin);
        runLoop.register(this);
    }

    void onReadable() {

        if (closed) {
            return;
        }

        if (currentRead == null) {
            runLoop.register(this);
            return;
        }

        int bytesRead = 0;
        try {
            bytesRead = channel.read(socketBuffer);
        } catch (IOException e) {
            close(e);
            return;
        }

        // Shouldn't be possible if both headers and body data can be incrementally decoded
        assert(bytesRead > 0 || socketBuffer.position() < socketBuffer.limit());
        assert(!currentRead.isDone());

        if (bytesRead > 0) {
            socketBuffer.flip();
            currentRead.set(socketBuffer);
            socketBuffer.compact();
            currentRead = readQueue.poll();
        }

        runLoop.register(this);
    }

    void onWriteable() {

        if (closed) {
            return;
        }

        if (currentWrite == null) {
            runLoop.register(this);
            return;
        }

        assert(!currentWrite.isDone());
        ByteBuffer[] writeData = currentWrite.pending();

        long bytesWritten = 0;
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
            currentWrite = writeQueue.poll();
        }

        runLoop.register(this);
    }

    int interestSet() {

        if (channel.isConnectionPending()) return SelectionKey.OP_CONNECT;

        int interestSet = 0;
        if (currentRead != null) interestSet = SelectionKey.OP_READ;
        if (currentWrite != null) interestSet |= SelectionKey.OP_WRITE;
        return interestSet;
    }

    void onClose() {
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

    ReadFuture read() {

        final ReadFuture readFuture = new ReadFuture();
        if (currentRead == null) {
            currentRead = readFuture;

            // If connected, try reading immediately
            if (isConnected()) {
                onReadable();
            }
        } else {
            readQueue.add(readFuture);
        }

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
        if (currentWrite == null) {
            currentWrite = writeFuture;

            // If connected, try writing immediately
            if (isConnected()) {
                onWriteable();
            }
        } else {
            writeQueue.add(writeFuture);
        }

        return writeFuture;
    }

    WriteFuture write(ByteBuffer data, int timeout, TimeUnit timeoutUnit) {
        return write(new ByteBuffer[]{data});
    }

    WriteFuture write(ByteBuffer[] data, int timeout, TimeUnit timeoutUnit) {
        return write(data);
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

        WriteFuture(ByteBuffer[] data) {
            this.data = data;
            bytesWritten = new ArrayList<>();
            totalBytesWritten = (long)0;
        }

        ByteBuffer[] pending() {
            return data;
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
            return writeQueue.contains(this) && super.cancel(mayInterruptIfRunning) && writeQueue.remove(this);
        }
    }
}
