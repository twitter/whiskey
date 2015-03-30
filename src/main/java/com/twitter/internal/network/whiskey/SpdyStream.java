package com.twitter.internal.network.whiskey;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

final class SpdyStream {

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
    private Request.Method redirectMethod;
    private URL redirectURL;
    private Integer statusCode;
    private final int priority;
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

    SpdyStream(boolean local, int priority) {
        this.local = local;
        closedLocally = !local;
        closedRemotely = false;
        this.priority = priority & 0x07;
    }

    SpdyStream(RequestOperation operation) {
        this.local = true;
        closedLocally = true;
        closedRemotely = false;
        this.operation = operation;
        this.priority = Math.min(7, (int) ((1d - operation.getCurrentRequest().getPriority()) * 8));
    }

    void open(int streamId, int sendWindow, int receiveWindow) {
        assert !open;
        this.streamId = streamId;
        this.sendWindow = sendWindow;
        this.receiveWindow = receiveWindow;
        open = true;
    }

    boolean reset() {
        return false;
    }

    int getStreamId() {
        return streamId;
    }

    int getPriority() {
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
    }

    boolean isOpen() {
        return open;
    }

    public boolean hasRecievedReply() {
        return receivedReply;
    }

    public int getReceiveWindow() {
        return receiveWindow;
    }

    public int getSendWindow() {
        return sendWindow;
    }

    public void increaseReceiveWindow(int delta) {
        if (Integer.MAX_VALUE - delta < receiveWindow) {
            receiveWindow = Integer.MAX_VALUE;
        } else {
            receiveWindow += delta;
        }
    }

    public void increaseSendWindow(int delta) {
        sendWindow += delta;
    }

    public void reduceReceiveWindow(int delta) {
        receiveWindow -= delta;
    }

    public void reduceSendWindow(int delta) {
        sendWindow -= delta;
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
                Integer status = Integer.valueOf(header.getValue());
                onStatus(status);
                break;
            case "location":
                try {
                    Request currentRequest = operation.getCurrentRequest();
                    redirectURL = new URL(currentRequest.getUrl(), header.getValue());
                } catch (MalformedURLException e) {
                    throw new IOException(
                        "Malformed URL received for redirect: " + header.getValue(), e);
                }
                break;
        }

        operation.getHeadersFuture().provide(header);
    }

    void onData(ByteBuffer data, boolean last) {
        // TODO: decompress compressed bodies
        if (!compressed) {
            operation.getBodyFuture().provide(data);
        }
    }

    void onStatus(int statusCode) throws IOException {

        if (finalResponse) {
            throw new ProtocolException("unexpected second response status received: " + statusCode);
        }

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
                    setFinalResponse();
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
            setFinalResponse();
        }
    }

    private void redirect() {
        Request redirect = new RequestBuilder(operation.getCurrentRequest())
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
    private void setFinalResponse() {
        finalResponse = true;
        operation.getHeadersFuture().release();
    }
}
