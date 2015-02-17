package com.twitter.internal.network.whiskey;

import java.io.IOException;
import java.nio.ByteBuffer;

final class SpdyStream {
    public static int STREAM_ID_UNSET = 0;

    private RequestOperation operation;
    private int streamId;
    private int priority;
    private int sendWindow;
    private int receiveWindow;
    private int unackedWindow;
    private boolean local;
    private boolean open;
    private boolean closedLocally;
    private boolean closedRemotely;
    private boolean receivedReply;

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

    void close(IOException e) {
        closedLocally = true;
        closedRemotely = true;
//        responseFuture.fail(e);
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

    void writeData(ByteBuffer data) {
    }
}
