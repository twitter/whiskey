package com.twitter.whiskey.net;

import com.twitter.whiskey.util.ZlibInflater;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.DataFormatException;

class SpdyStream {

    private static final Set<String> INVALID_HEADERS;
    static {
        INVALID_HEADERS = new HashSet<String>() {{
            add(Headers.CONNECTION);
            add(Headers.KEEP_ALIVE);
            add(Headers.PROXY_CONNECTION);
            add(Headers.TRANSFER_ENCODING);
        }};
    }

    private RequestOperation operation;
    private Request request;
    private Request.Method redirectMethod;
    private ZlibInflater inflater;
    private URL redirectURL;
    private Integer statusCode;
    private final byte priority;
    private int streamId;
    private int sendWindow;
    private int receiveWindow;
    private int unackedWindow;
    private boolean compressed;
    private boolean local;
    private boolean open;
    private boolean closedLocally;
    private boolean closedRemotely;
    private boolean receivedReply;
    private boolean finalResponse;

    SpdyStream(boolean local, byte priority) {
        assert(priority == (byte) (priority & 0x07));
        this.local = local;
        this.priority = priority;
        closedLocally = !local;
        closedRemotely = false;
    }

    static SpdyStream newStream(RequestOperation operation) {
        if (operation.getCurrentRequest().getBodyData() != null) {
            return new SpdyStream.Buffered(operation);
        } else if (operation.getCurrentRequest().getBodyStream() != null) {
            return new SpdyStream.Streamed(operation);
        } else {
            return new SpdyStream(operation);
        }
    }

    SpdyStream(RequestOperation operation) {
        this(true, (byte) Math.min(7, (int) ((1d - operation.getCurrentRequest().getPriority()) * 8)));
        this.operation = operation;
        request = operation.getCurrentRequest();
    }

    void open(int streamId, int sendWindow, int receiveWindow) {
        assert !open;
        this.streamId = streamId;
        this.sendWindow = sendWindow;
        this.receiveWindow = receiveWindow;
        open = true;
    }

    RequestOperation getOperation() {
        return operation;
    }

    int getStreamId() {
        return streamId;
    }

    byte getPriority() {
        return priority;
    }

    void setStreamId(int streamId) {
        this.streamId = streamId;
    }

    boolean isLocal() {
        return local;
    }

    boolean isClosedLocally() {
        return closedLocally;
    }

    boolean isClosedRemotely() {
        return closedRemotely;
    }

    boolean isClosed() {
        return closedLocally && closedRemotely;
    }

    void closeLocally() {
        closedLocally = true;
    }

    void closeRemotely() {
        closedRemotely = true;
    }

    void close(Throwable e) {
        closedLocally = true;
        closedRemotely = true;
        operation.fail(e);
    }

    void complete() {
        if (redirectMethod != null && redirectURL != null) {
            redirect();
        }

        if (!finalResponse) {
            finalizeResponse();
        }

        operation.complete(statusCode);
    }

    boolean isOpen() {
        return open;
    }

    boolean hasReceivedReply() {
        return receivedReply;
    }

    int getReceiveWindow() {
        return receiveWindow;
    }

    int getSendWindow() {
        return sendWindow;
    }

    void increaseReceiveWindow(int delta) {
        if (Integer.MAX_VALUE - delta < receiveWindow) {
            receiveWindow = Integer.MAX_VALUE;
        } else {
            receiveWindow += delta;
        }
    }

    void increaseSendWindow(int delta) {
        sendWindow += delta;
    }

    void reduceReceiveWindow(int delta) {
        receiveWindow -= delta;
    }

    void reduceSendWindow(int delta) {
        sendWindow -= delta;
    }

    Headers getCanonicalHeaders() {

        Headers canonical = new Headers(request.getHeaders());
        canonical.put(":path", request.getUrl().getPath());
        canonical.put(":method", request.getMethod().toString());
        canonical.put(":version", "HTTP/1.1");
        canonical.put(":host", request.getUrl().getHost());
        canonical.put(":scheme", "http");
        return canonical;
    }

    boolean hasPendingData() {
        return false;
    }

    ByteBuffer readData(int length) throws IOException {
        throw new UnsupportedOperationException();
    }

    void onReply() {
        receivedReply = true;
    }

    void onHeader(Header header) throws IOException {

        if (INVALID_HEADERS.contains(header.getKey())) {
            throw new SpdyProtocolException("invalid header for SPDY response: " + header.getKey());
        }

        switch(header.getKey()) {
            case ":status":
                Integer status;
                try {
                    status = Integer.valueOf(header.getValue().substring(0, 3));
                } catch (NumberFormatException e) {
                    throw new IOException("invalid HTTP response: " + header.getValue());
                }
                onStatus(status);
                break;
            case "location":
                try {
                    Request currentRequest = operation.getCurrentRequest();
                    redirectURL = new URL(currentRequest.getUrl(), header.getValue());
                } catch (MalformedURLException e) {
                    throw new IOException(
                        "malformed URL received for redirect: " + header.getValue(), e);
                }
                break;
            case "content-encoding":
                String value = header.getValue();
                if (value.equalsIgnoreCase("gzip")) {
                    compressed = true;
                    inflater = new ZlibInflater(ZlibInflater.Wrapper.GZIP);
                } else if (value.equalsIgnoreCase("deflate")) {
                    compressed = true;
                    inflater = new ZlibInflater(ZlibInflater.Wrapper.UNKNOWN);
                }
                break;
        }

        operation.getHeadersFuture().provide(header);
    }

    void onData(ByteBuffer data) throws DataFormatException {

        if (!data.hasRemaining()) return;
        if (!compressed) {
            operation.getBodyFuture().provide(data);
        } else {
            // Set chunk size to twice the next power of 2
            assert(data.remaining() < Integer.MAX_VALUE >> 2);
            int chunkSize = Integer.highestOneBit(data.remaining()) << 2;
            ByteBuffer decompressed = ByteBuffer.allocate(chunkSize);
            inflater.setInput(data.array(), data.arrayOffset() + data.position(), data.remaining());

            int bytesWritten = 0;
            do {
                bytesWritten = inflater.inflate(
                    decompressed.array(), decompressed.arrayOffset() + decompressed.position(), decompressed.remaining());
                decompressed.position(decompressed.position() + bytesWritten);
                if (inflater.getRemaining() > 0 && !decompressed.hasRemaining()) {
                    decompressed.flip();
                    operation.getBodyFuture().provide(decompressed);
                    decompressed = ByteBuffer.allocate(chunkSize);
                }
            } while (!inflater.needsInput() && !inflater.finished());

            decompressed.flip();
            operation.getBodyFuture().provide(decompressed);
            assert(inflater.getRemaining() == 0);
        }
    }

    void onStatus(int statusCode) throws IOException {

        if (finalResponse) {
            throw new ProtocolException("unexpected second response status received: " + statusCode);
        }

        this.statusCode = statusCode;
        if (statusCode >= 300 && statusCode < 400 && operation.getRemainingRedirects() > 0) {
            Request currentRequest = operation.getCurrentRequest();
            Request.Method currentMethod = currentRequest.getMethod();

            if (statusCode == 301 || statusCode == 307) {
            /* RFC 2616: If the [status code] is received in response to a request other than
             * GET or HEAD, the user agent MUST NOT automatically redirect the request unless
             * it can be confirmed by the user, since this might change the conditions under
             * which the request was issued.
             */
                if (currentMethod == Request.Method.GET || currentMethod == Request.Method.HEAD) {
                    redirectMethod = currentMethod;
                } else {
                    finalizeResponse();
                }
            } else if (statusCode == 302 || statusCode == 303) {
            /*
             * This implementation follows convention over specification by changing the request
             * method to GET on a 302 redirect. Note this behavior violates RFC 1945, 2068,
             * and 2616, but is also expected by most servers and clients.
             */
                redirectMethod = Request.Method.GET;
            }
        } else if (statusCode != 100) {
            finalizeResponse();
        }
    }

    private void redirect() {
        Request redirect = new Request.Builder(operation.getCurrentRequest())
            .method(redirectMethod)
            .url(redirectURL)
            .body()
            .create();
        operation.redirect(redirect);
    }

    private boolean retry() {
        if (receivedReply || !local) return false;
        operation.retry();
        return true;
    }

    /**
     * The incoming response is the one to pass on to the client: no more
     * redirects will be followed and retry behavior is disallowed.
     */
    private void finalizeResponse() {
        finalResponse = true;
        operation.getHeadersFuture().release();
    }

    private static class Buffered extends SpdyStream {
        private ByteBuffer[] data;
        private int dataIndex;

        Buffered(RequestOperation operation) {
            super(operation);
            this.data = operation.getCurrentRequest().getBodyData();
            dataIndex = 0;
        }

        @Override
        boolean hasPendingData() {
            while (dataIndex < data.length) {
                if (data[dataIndex].hasRemaining()) return true;
                dataIndex++;
            }
            return false;
        }

        ByteBuffer readData(int length) throws IOException {

            while (dataIndex < data.length) {
                int available = data[dataIndex].remaining();
                if (available > 0) {
                    int bytesToRead = Math.min(length, available);
                    int oldLimit = data[dataIndex].limit();
                    int sliceLimit = data[dataIndex].position() + bytesToRead;
                    data[dataIndex].limit(sliceLimit);
                    ByteBuffer slice = data[dataIndex].slice();
                    data[dataIndex].limit(oldLimit);
                    data[dataIndex].position(sliceLimit);
                    return slice;
                }
                dataIndex++;
            }

            throw new IOException("no pending data");
        }

        boolean retry() {
            for (ByteBuffer buffer : data) {
                buffer.position(0);
            }
            return super.retry();
        }
    }

    private static class Streamed extends SpdyStream {

        // Limit the amount of data that may be read from a stream before it is
        // considered "un-retryable" even if it supports mark and reset. Note that
        // BufferedInputStream apparently possesses some especially undesirable
        // behavior: it will buffer up to this limit in memory, even if the
        // underlying stream natively supports "free" mark and reset behaviors.
        private static final int MARK_READ_LIMIT = Integer.MAX_VALUE;

        InputStream dataStream;
        boolean pending = true;

        Streamed(RequestOperation operation) {

            super(operation);
            this.dataStream = operation.getCurrentRequest().getBodyStream();
            if (dataStream.markSupported()) {
                dataStream.mark(MARK_READ_LIMIT);
            }
        }

        @Override
        boolean hasPendingData() {

            if (!pending) return false;
            try {
                pending = dataStream.available() > 0;
            } catch (IOException e) {
                pending = false;
            }
            return pending;
        }

        @Override
        ByteBuffer readData(int length) throws IOException {

            int bytesToRead = Math.min(length, dataStream.available());
            byte[] data = new byte[bytesToRead];
            int bytesRead = dataStream.read(data);
            return ByteBuffer.wrap(data, 0, bytesRead);
        }

        boolean retry() {

            if (dataStream.markSupported()) {
                try {
                    dataStream.reset();
                } catch (IOException e) {
                    return false;
                }
            }
            return super.retry();
        }
    }
}
