package com.twitter.internal.network.whiskey;

import java.io.IOException;
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

    public static int STREAM_ID_UNSET = 0;

    private RequestOperation operation;
    private Request.Method redirectMethod;
    private URL redirectURL;
    private Integer statusCode;
    private int streamId;
    private int priority;
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

    SpdyStream() {

    }

    SpdyStream(boolean local) {
        this.local = local;
        closedLocally = !local;
        closedRemotely = false;
    }

    SpdyStream(RequestOperation operation) {
        this.local = true;
        closedLocally = true;
        closedRemotely = false;
        this.operation = operation;
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

    void setPriority(int priority) {
        this.priority = priority;
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

    void closeSilently() {
        closedLocally = true;
        closedRemotely = true;
    }

    void close(IOException e) {
        closedLocally = true;
        closedRemotely = true;
        operation.fail(e);
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

    void receivedHeader(Headers.Header header) throws ProtocolException {

        if (INVALID_HEADERS.contains(header.getKey())) {
            throw new ProtocolException("invalid header for SPDY response: " + header.getKey());
        }

        switch(header.getKey()) {
            case ":status":
                Integer status = Integer.valueOf(header.getValue());
                receivedStatus(status);
        }
    }

    void receivedData(ByteBuffer data, boolean last) {
        // TODO: decompress compressed bodies
        if (!compressed) {
            operation.getBodyFuture().provide(data, last);
        }
    }

    void receivedStatus(int statusCode) throws ProtocolException {

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

            if (redirectMethod != null && redirectURL != null) {
                closeSilently();
                Request redirect = new RequestBuilder(currentRequest)
                    .method(redirectMethod)
                    .url(redirectURL)
                    .body(null)
                    .create();
                operation.redirect(redirect);
            }
        } else if (statusCode != 100) {
            finalizeResponse();
        }
    }

    private void finalizeResponse() {
        finalResponse = true;
        //operation.getHeadersFuture().release();
    }
}
