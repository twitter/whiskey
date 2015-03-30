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

    private final Origin origin;
    private final RunLoop runLoop;

    private boolean closed = false;

    private SocketChannel channel;
    private SelectionKey key;

    private ConnectFuture connectFuture;
    private CloseFuture closeFuture;
    private Deque<ReadFuture> readQueue = new ArrayDeque<>(1);
    private Deque<WriteFuture> writeQueue = new ArrayDeque<>(32);

    Socket(Origin origin, RunLoop runLoop) {
        this.origin = origin;
        this.runLoop = runLoop;
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

    CloseFuture getCloseFuture() {
        return closeFuture;
    }

    ReadFuture read() {
        return read(new ReadFuture());
    }

    ReadFuture read(final ReadFuture readFuture) {
        runLoop.execute(new Runnable() {
            public void run() {
                getReadQueue().add(readFuture);

                if (channel != null && getReadQueue().size() == 1) {
                    reregister();
                }
            }
        });

        return readFuture;
    }

    ReadFuture read(ByteBuffer readBuffer) {
        // TODO: resolve this
        return read();
    }

    ReadFuture read(int timeout, TimeUnit timeoutUnit) {
        return read();
    }

    WriteFuture write(ByteBuffer data) {
        return write(new ByteBuffer[]{data});
    }

    WriteFuture write(ByteBuffer[] data) {
        return write(new WriteFuture(data));
    }

    WriteFuture write(ByteBuffer data, int timeout, TimeUnit timeoutUnit) {
        return write(new ByteBuffer[]{data});
    }

    WriteFuture write(ByteBuffer[] data, int timeout, TimeUnit timeoutUnit) {
        return write(data);
    }

    WriteFuture write(final WriteFuture writeFuture) {
        runLoop.execute(new Runnable() {
            public void run() {
                getWriteQueue().add(writeFuture);

                if (isConnected() && getWriteQueue().size() == 1) {
                    reregister();
                }
            }
        });

        return writeFuture;
    }

    Deque<ReadFuture> getReadQueue() {
        return readQueue;
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

    void finishConnect() throws IOException {
        closeFuture = new CloseFuture();
        connectFuture.set(origin);
        reregister();
    }

    void failConnect(Throwable thr) {
        if (!connectFuture.isDone()) {
            connectFuture.fail(thr);
        }
    }

    @Override
    public void onReadable() {
        
        if (closed) {
            return;
        }

        Deque<ReadFuture> readQueue = getReadQueue();
        
        if (readQueue.isEmpty()) {
            reregister();
            return;
        }

        ReadFuture currentRead = readQueue.peek();
        assert (!currentRead.isDone());

        boolean complete;
        try {
            complete = currentRead.doRead(channel);
        } catch (IOException e) {
            close(e);
            return;
        }

        if (complete) {
            readQueue.poll();
        }

        reregister();
    }

    @Override
    public void onWriteable() {

        if (closed) {
            return;
        }

        Deque<WriteFuture> writeQueue = getWriteQueue();

        if (writeQueue.isEmpty()) {
            reregister();
            return;
        }

        WriteFuture currentWrite = writeQueue.peek();
        assert(!currentWrite.isDone());

        boolean complete;
        try {
             complete = currentWrite.doWrite();
        } catch (IOException e) {
            close(e);
            return;
        }

        if (complete) {
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

    @Override
    public void setSelectionKey(SelectionKey key) {
        this.key = key;
    }

    public boolean isConnected() {
        return !closed && channel != null && channel.isConnected();
    }

    private int interestSet() {
        if (channel.isConnectionPending()) return SelectionKey.OP_CONNECT;

        int interestSet = 0;
        if (!getReadQueue().isEmpty()) interestSet = SelectionKey.OP_READ;
        if (!getWriteQueue().isEmpty()) interestSet |= SelectionKey.OP_WRITE;
        return interestSet;
    }

    @Override
    public void onClose(Throwable e) {

        if (closed) return;
        closed = true;
        key = null;
        closeFuture.fail(e);
    }

    boolean isSecure() {
        return false;
    }

    private void close(Throwable e) {

        if (closed) return;
        closed = true;
        if (key != null) key.cancel();
        closeFuture.fail(e);
    }

    public void close() {

        if (closed) return;
        closed = true;
        if (key != null) key.cancel();

        try {
            channel.close();
        } catch (IOException ignored) {
        }

        closeFuture.set(origin);
    }

    public Request.Protocol getProtocol() {
        return Request.Protocol.SPDY_3_1;
    }

    class ConnectFuture extends CompletableFuture<Origin> {
    }

    class CloseFuture extends CompletableFuture<Origin> {
    }

    class ReadFuture extends CompletableFuture<ByteBuffer> {

        private static final int BUFFER_SIZE = 18 * 1024;

        private final ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

        boolean doRead(SocketChannel channel) throws IOException {

            ByteBuffer buffer = getBuffer();

            int bytesRead = channel.read(buffer);

            assert (bytesRead != 0);

            if (bytesRead > 0) {
                buffer.flip();
                set(buffer);
            } else {
                fail(new IOException("connection closed"));
            }

            return true;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return getReadQueue().contains(this) && super.cancel(mayInterruptIfRunning) && getReadQueue().remove(this);
        }

        public ByteBuffer getBuffer() {
            return buffer;
        }
    }

    class WriteFuture extends ReactiveFuture<Long, Long> {
        private ByteBuffer[] data;
        ArrayList<Long> bytesWritten;
        Long totalBytesWritten;

        WriteFuture(ByteBuffer[] data) {
            this.data = data;
            bytesWritten = new ArrayList<>();
            totalBytesWritten = (long)0;
        }

        ByteBuffer[] pending() throws IOException {
            return data;
        }

        public void setPending(ByteBuffer[] pending) {
            this.data = pending;
        }

        boolean doWrite() throws IOException {
            long bytesWritten = channel.write(data);

            ByteBuffer finalData = data[data.length - 1];
            boolean writeComplete = finalData.position() == finalData.limit();
            provide(bytesWritten);
            if (writeComplete) finish();
            return writeComplete;
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
        boolean complete() {
            return set(totalBytesWritten);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return getWriteQueue().contains(this) && super.cancel(mayInterruptIfRunning) && getWriteQueue().remove(this);
        }

    }
}
