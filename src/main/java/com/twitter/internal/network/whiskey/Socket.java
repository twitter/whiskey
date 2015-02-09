package com.twitter.internal.network.whiskey;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

class Socket {
    private static final int SOCKET_BUFFER_SIZE = 65536;

    private boolean connected = false;
    private boolean closed = false;

    private Selector selector;
    private SelectionKey selectionKey;
    private SocketChannel channel;
    private SSLContext context;
    private SSLEngine engine;

    private ByteBuffer socketBuffer;
    private ConnectFuture connectFuture;
    private ReadFuture currentRead;
    private WriteFuture currentWrite;
    private LinkedHashSet<ReadFuture> readQueue;
    private LinkedHashSet<WriteFuture> writeQueue;
    private RunLoop executor;
    private Origin origin;

    Socket(Origin origin, RunLoop executor) {
        this.origin = origin;
        this.executor = executor;
        socketBuffer = ByteBuffer.allocate(SOCKET_BUFFER_SIZE);
    }

    ConnectFuture connect() {

        connectFuture = new ConnectFuture();
        try {
            channel = SocketChannel.open();
            channel.configureBlocking(false);
            executor.register(this);
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

    void onConnect() throws IOException {

        channel.finishConnect();
        finishConnect();
    }

    void finishConnect() throws IOException {

        connectFuture.set(origin);
        executor.register(this);
    }

    void onReadable() {

        if (closed) {
            return;
        }

        if (currentRead == null) {
            executor.register(this);
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
            finishRead();
        }

        executor.register(this);
    }

    void onWriteable() {

        if (closed) {
            return;
        }

        if (currentWrite == null) {
            executor.register(this);
            return;
        }

        assert(!currentWrite.isDone());
        ByteBuffer[] writeData = currentWrite.pending();

        long bytesWritten = 0;
        try {
             bytesWritten = channel.write(writeData);
             currentWrite.provide(bytesWritten);
        } catch (IOException e) {
            close(e);
            return;
        }

        // Write complete
        ByteBuffer finalData = writeData[writeData.length - 1];

        if (finalData.position() == finalData.limit()) {
            currentWrite.complete();
            finishWrite();
        }

        executor.register(this);
    }

    int interestSet() {

        if (channel.isConnectionPending()) return SelectionKey.OP_CONNECT;

        int interestSet = 0;
        if (!readQueue.isEmpty()) interestSet = SelectionKey.OP_READ;
        if (!writeQueue.isEmpty()) interestSet |= SelectionKey.OP_WRITE;
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

    void finishRead() {

        currentRead = null;
        if (!readQueue.isEmpty()) {
            Iterator<ReadFuture> i = readQueue.iterator();
            currentRead = i.next();
            i.remove();
        }
    }

    void finishWrite() {

        currentWrite = null;
        if (!writeQueue.isEmpty()) {
            Iterator<WriteFuture> i = writeQueue.iterator();
            currentWrite = i.next();
            i.remove();
        }
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
        Long bytesWritten;

        public WriteFuture(ByteBuffer[] data) {
            this.data = data;
            bytesWritten = (long)0;
        }

        public ByteBuffer[] pending() {
            return data;
        }

        @Override
        public void accumulate(Long element) {
            this.bytesWritten += element;
        }

        public boolean complete() {
            return set(bytesWritten);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return writeQueue.contains(this) && super.cancel(mayInterruptIfRunning) && writeQueue.remove(this);
        }
    }
}
