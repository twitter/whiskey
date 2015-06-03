/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.net;

import com.twitter.whiskey.futures.CompletableFuture;
import com.twitter.whiskey.futures.Listener;
import com.twitter.whiskey.nio.Socket;
import com.twitter.whiskey.futures.Inline;
import com.twitter.whiskey.util.Origin;
import com.twitter.whiskey.util.PlatformAdapter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.DataFormatException;

import static com.twitter.whiskey.net.SpdyConstants.*;

/**
 * SPDY-specific implementation of {@link Session}. Logically represents a
 * session as described in the SPDY draft protocol.
 *
 * @author Michael Schore
 */
class SpdySession implements Session, SpdyFrameDecoderDelegate {

    private static final Map<Origin, SpdySettings> storedSettings = new HashMap<>();
    private static final int DEFAULT_BUFFER_SIZE = 65536;

    private final Origin origin;
    private final ClientConfiguration configuration;
    private final CompletableFuture<Void> closeFuture;
    private final SessionManager manager;
    private final SpdyFrameDecoder frameDecoder;
    private final SpdyFrameEncoder frameEncoder;
    private final SpdyStreamManager activeStreams = new SpdyStreamManager();
    private final Socket socket;

    private ByteBuffer inputBuffer;
    private Map<Integer, Long> sentPingMap = new TreeMap<>();
    private int lastGoodStreamId = 0;
    private int nextStreamId = 1;
    private int nextPingId = 1;
    private int initialSendWindow = DEFAULT_INITIAL_WINDOW_SIZE;
    private int initialReceiveWindow;
    private int sessionSendWindow = DEFAULT_INITIAL_WINDOW_SIZE;
    private int sessionReceiveWindow;
    private int localMaxConcurrentStreams = 0;
    private int remoteMaxConcurrentStreams = 100;
    private long latency = -1;
    private boolean receivedGoAwayFrame = false;
    private boolean sentGoAwayFrame = false;
    private boolean active = false;
    private boolean error = false;

    SpdySession(SessionManager manager, ClientConfiguration configuration, Socket socket) {

        this.configuration = configuration;
        this.manager = manager;
        this.origin = manager.getOrigin();
        this.socket = socket;

        frameDecoder = new SpdyFrameDecoder(SpdyVersion.SPDY_3_1, this);
        frameEncoder = new SpdyFrameEncoder(SpdyVersion.SPDY_3_1);

        initialReceiveWindow = configuration.getStreamReceiveWindow();
        sessionReceiveWindow = configuration.getSessionReceiveWindow();
        localMaxConcurrentStreams = configuration.getMaxPushStreams();

        closeFuture = new CompletableFuture<>();
        socket.addCloseListener(new SocketCloseListener());
        sendClientSettings();
        sendPing();

        int windowDelta = sessionReceiveWindow - DEFAULT_INITIAL_WINDOW_SIZE;
        sendWindowUpdate(SPDY_SESSION_STREAM_ID, windowDelta);
        manager.poll(this, getCapacity());

        inputBuffer = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE);
        listen();
    }

    private void listen() {
        socket.read(inputBuffer).addListener(new Inline.Listener<ByteBuffer>() {
            @Override
            public void onComplete(ByteBuffer result) {
                if (inError()) return; // session is unrecoverable, halt decoding
                frameDecoder.decode(result);
                result.compact();
                listen();
            }
        });
    }

    @Override
    public boolean isOpen() {
        return !receivedGoAwayFrame && socket.isConnected();
    }

    @Override
    public boolean isConnected() {
        return socket.isConnected();
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public boolean isClosed() {
        return receivedGoAwayFrame || !socket.isConnected();
    }

    @Override
    public boolean isDraining() {
        return receivedGoAwayFrame && socket.isConnected();
    }

    @Override
    public boolean isDisconnected() {
        return !socket.isConnected();
    }

    private boolean inError() {
        return error;
    }

    @Override
    public int getCapacity() {
        return remoteMaxConcurrentStreams - activeStreams.getLocalSize();
    }

    @Override
    public boolean wasActive() {
        return false;
    }

    @Override
    public void queue(RequestOperation operation) {

        final SpdyStream stream = SpdyStream.newStream(operation);
        final int streamId = nextStreamId;
        nextStreamId += 2;
        stream.open(streamId, initialSendWindow, configuration.getStreamReceiveWindow());
        activeStreams.add(stream);

        // TODO: implement via interrupts to avoid unnecessary calls
        operation.addListener(new Inline.Listener<Response>() {
            @Override
            public void onError(Throwable throwable) {
                if (activeStreams.contains(stream)) {
                    activeStreams.remove(stream);
                    sendRstStream(streamId, SPDY_STREAM_CANCEL);
                }
            }
        });
        boolean hasBody = stream.hasPendingData();
        sendSynStream(
            streamId, stream.getPriority(), !hasBody, stream.getCanonicalHeaders());
        if (hasBody) {
            sendData(stream);
        }
    }

    @Override
    public void addCloseListener(Listener<Void> listener) {
        closeFuture.addListener(listener);
    }

    /* SpdyFrameDecoderDelegate */
    @Override
    public void readDataFrame(int streamId, boolean last, ByteBuffer data) {
    /*
     * SPDY Data frame processing requirements:
     *
     * If an endpoint receives a data frame for a Stream-ID which is not open
     * and the endpoint has not sent a GOAWAY frame, it must issue a stream error
     * with the error code INVALID_STREAM for the Stream-ID.
     *
     * If an endpoint which created the stream receives a data frame before receiving
     * a SYN_REPLY on that stream, it is a protocol error, and the recipient must
     * issue a stream error with the status code PROTOCOL_ERROR for the Stream-ID.
     *
     * If an endpoint receives multiple data frames for invalid Stream-IDs,
     * it may close the session.
     *
     * If an endpoint refuses a stream it must ignore any data frames for that stream.
     *
     * If an endpoint receives a data frame after the stream is half-closed from the
     * sender, it must send a RST_STREAM frame with the status STREAM_ALREADY_CLOSED.
     *
     * If an endpoint receives a data frame after the stream is closed, it must send
     * a RST_STREAM frame with the status PROTOCOL_ERROR.
     */

        System.err.println(
            "read DATA\n--> Stream-ID = " + streamId + "\n--> Size = " + data.remaining() +
                "\n--> Last: " + last
        );
        SpdyStream stream = activeStreams.get(streamId);

        // Check if session flow control is violated
        if (sessionReceiveWindow < data.remaining()) {
            closeWithError(new SpdySessionException("session flow control violatian"));
            return;
        }

        // Check if we received a data frame for a valid Stream-ID
        if (stream == null) {
            if (streamId < lastGoodStreamId) {
                sendRstStream(streamId, SPDY_STREAM_PROTOCOL_ERROR);
            } else if (!sentGoAwayFrame) {
                sendRstStream(streamId, SPDY_STREAM_INVALID_STREAM);
            }
            return;
        }

        // Check if we received a data frame for a stream which is half-closed
        if (stream.isClosedRemotely()) {
            sendRstStream(streamId, SPDY_STREAM_STREAM_ALREADY_CLOSED);
            return;
        }

        // Check if we received a data frame before receiving a SYN_REPLY
        if (stream.isLocal() && !stream.hasReceivedReply()) {
            sendRstStream(streamId, SPDY_STREAM_PROTOCOL_ERROR);
            return;
        }

    /*
     * SPDY Data frame flow control processing requirements:
     *
     * Recipient should not send a WINDOW_UPDATE frame as it consumes the last data frame.
     */

        // Check if stream flow control is violated
        if (stream.getReceiveWindow() < data.remaining()) {
            sendRstStream(streamId, SPDY_STREAM_FLOW_CONTROL_ERROR);
            return;
        }

        // Update session receive window size
        sessionReceiveWindow -= data.remaining();

        // Send a WINDOW_UPDATE frame if less than half the sesion window size remains
        if (sessionReceiveWindow <= initialReceiveWindow / 2) {
            int deltaWindowSize = initialReceiveWindow - sessionReceiveWindow;
            sendWindowUpdate(SPDY_SESSION_STREAM_ID, deltaWindowSize);
        }

        // Update stream receive window size
        stream.reduceReceiveWindow(data.remaining());

        if (stream.getReceiveWindow() <= initialReceiveWindow / 2) {
            int deltaWindowSize = initialReceiveWindow - stream.getReceiveWindow();
            stream.increaseReceiveWindow(deltaWindowSize);
            sendWindowUpdate(streamId, deltaWindowSize);
        }

        try {
            stream.onData(data);
        } catch (DataFormatException e) {
            sendRstStream(streamId, SPDY_STREAM_INTERNAL_ERROR);
            activeStreams.remove(stream);
            stream.close(e);
        }

        if (last) {
            stream.closeRemotely();
            if (stream.isClosed()) {
                activeStreams.remove(stream);
                stream.complete();
            }
        }
    }

    @Override
    public void readSynStreamFrame(int streamId, int associatedToStreamId, byte priority, boolean last, boolean unidirectional) {
    /*
     * SPDY SYN_STREAM frame processing requirements:
     *
     * If an endpoint receives a SYN_STREAM with a Stream-ID that is less than
     * any previously received SYN_STREAM, it must issue a session error with
     * the status PROTOCOL_ERROR.
     *
     * If an endpoint receives multiple SYN_STREAM frames with the same active
     * Stream-ID, it must issue a stream error with the status code PROTOCOL_ERROR.
     *
     * The recipient can reject a stream by sending a stream error with the
     * status code REFUSED_STREAM.
     */

        if (streamId <= lastGoodStreamId) {
            sendRstStream(streamId, SPDY_STREAM_PROTOCOL_ERROR);
            return;
        }

        if (receivedGoAwayFrame || activeStreams.getRemoteSize() >= localMaxConcurrentStreams) {
            sendRstStream(streamId, SPDY_STREAM_REFUSED_STREAM);
            return;
        }

        final SpdyStream parent = activeStreams.get(associatedToStreamId);
        if (parent == null || parent.isClosed()) {
            sendRstStream(streamId, SPDY_STREAM_PROTOCOL_ERROR);
        }

        final SpdyStream stream = new SpdyStream.Pushed(parent, priority);
        stream.open(streamId, initialSendWindow, initialReceiveWindow);

        lastGoodStreamId = streamId;
        activeStreams.add(stream);
    }

    @Override
    public void readSynReplyFrame(int streamId, boolean last) {
    /*
     * SPDY SYN_REPLY frame processing requirements:
     *
     * If an endpoint receives multiple SYN_REPLY frames for the same active Stream-ID
     * it must issue a stream error with the status code STREAM_IN_USE.
     */

        System.err.println("read SYN_REPLY\n--> Stream-ID = " + streamId + "\n--> Last = " + last);
        SpdyStream stream = activeStreams.get(streamId);

        // Check if this is a reply for an active stream
        if (stream == null) {
            sendRstStream(streamId, SPDY_STREAM_INVALID_STREAM);
            return;
        }

        // Check if we have received multiple frames for the same Stream-ID
        if (stream.hasReceivedReply()) {
            sendRstStream(streamId, SPDY_STREAM_STREAM_IN_USE);
            return;
        }

        active = true;
        stream.onReply();

        if (last) {
            stream.closeRemotely();
            // Defer removing stream from activeStreams until we receive headersEnd
        }
    }

    @Override
    public void readRstStreamFrame(int streamId, int statusCode) {
    /*
    * SPDY RST_STREAM frame processing requirements:
    *
    * After receiving a RST_STREAM on a stream, the receiver must not send
    * additional frames on that stream.
    *
    * An endpoint must not send a RST_STREAM in response to a RST_STREAM.
    */

        SpdyStream stream = activeStreams.get(streamId);

        if (stream != null) {
            activeStreams.remove(stream);
            if (statusCode == SPDY_STREAM_REFUSED_STREAM) {
                stream.closeRetryably(new SpdyStreamException(statusCode));
                return;
            }
            stream.close(new SpdyStreamException(statusCode));
        }
    }

    @Override
    public void readSettingsFrame(boolean clearPersisted) {
    /*
     * SPDY SETTINGS frame processing requirements:
     *
     * When a client connects to a server, and the server persists settings
     * within the client, the client should return the persisted settings on
     * future connections to the same origin and IP address and TCP port (the
     * "origin" is the set of scheme, host, and port from the URI).
     */

        System.err.println("read SETTINGS\n");
        if (clearPersisted) {
            storedSettings.remove(origin);
        }
    }

    @Override
    public void readSetting(int id, int value, boolean persistValue, boolean persisted) {

        if (persisted) {
            closeWithError(new SpdySessionException("received server-persisted SETTINGS"));
            return;
        }

        int delta;
        switch(id) {

            case SpdySettings.MAX_CONCURRENT_STREAMS:
                delta = value - remoteMaxConcurrentStreams;
                remoteMaxConcurrentStreams = value;
                if (delta > 0) {
                    manager.poll(this, delta);
                }
                break;

            case SpdySettings.INITIAL_WINDOW_SIZE:
                delta = value - initialSendWindow;
                for (SpdyStream stream : activeStreams) {
                    if (!stream.isClosedLocally()) {
                        stream.increaseSendWindow(delta);
                        if (delta > 0) {
                            sendData(stream);
                        }
                    }
                }
                break;

            default:
        }

        if (persistValue) {
            SpdySettings settings = storedSettings.get(origin);
            if (settings == null) {
                settings = new SpdySettings();
                storedSettings.put(origin, settings);
            }
            settings.setValue(id, value);
        }
    }

    @Override
    public void readSettingsEnd() {
    }

    @Override
    public void readPingFrame(int id) {
    /*
     * SPDY PING frame processing requirements:
     *
     * Receivers of a PING frame should send an identical frame to the sender
     * as soon as possible.
     *
     * Receivers of a PING frame must ignore frames that it did not initiate
     */

        System.err.println("read PING\n--> Ping-ID = " + id);

        if (id % 2 == 0) {
            sendPingResponse(id);
        } else {
            Long sentTime = sentPingMap.get(id);
            if (sentTime == null) {
                return;
            }

            sentPingMap.remove(id);
            latency = sentTime - System.currentTimeMillis();
        }
    }

    @Override
    public void readGoAwayFrame(int lastGoodStreamId, int statusCode) {

        receivedGoAwayFrame = true;

        for (SpdyStream stream : activeStreams) {
            if (stream.isLocal() && stream.getStreamId() > lastGoodStreamId) {
                activeStreams.remove(stream);
                stream.closeRetryably(new SpdySessionException(statusCode));
            }
        }
    }

    @Override
    public void readHeadersFrame(int streamId, boolean last) {

        SpdyStream stream = activeStreams.get(streamId);

        if (stream == null || stream.isClosedRemotely()) {
            sendRstStream(streamId, SPDY_STREAM_INVALID_STREAM);
        }
    }

    @Override
    public void readWindowUpdateFrame(int streamId, int deltaWindowSize) {
    /*
     * SPDY WINDOW_UPDATE frame processing requirements:
     *
     * Receivers of a WINDOW_UPDATE that cause the window size to exceed 2^31
     * must send a RST_STREAM with the status code FLOW_CONTROL_ERROR.
     *
     * Sender should ignore all WINDOW_UPDATE frames associated with a stream
     * after sending the last frame for the stream.
     */

        if (streamId == SPDY_SESSION_STREAM_ID) {
            // Check for numerical overflow
            if (sessionSendWindow > Integer.MAX_VALUE - deltaWindowSize) {
                closeWithError(new SpdySessionException("session send window exceeded max value"));
                return;
            }

            sessionSendWindow += deltaWindowSize;
            for (SpdyStream stream : activeStreams) {
                if (!stream.isClosedLocally()) sendData(stream);
                if (sessionSendWindow == 0) break;
            }

            return;
        }

        SpdyStream stream = activeStreams.get(streamId);

        // Ignore frames for non-existent or half-closed streams
        if (stream == null || stream.isClosedLocally()) {
            return;
        }

        // Check for numerical overflow
        if (stream.getSendWindow() > Integer.MAX_VALUE - deltaWindowSize) {
            sendRstStream(streamId, SPDY_STREAM_FLOW_CONTROL_ERROR);
            activeStreams.remove(stream);
            stream.close(new SpdyStreamException(SPDY_STREAM_FLOW_CONTROL_ERROR));
        }

        stream.increaseSendWindow(deltaWindowSize);
        if (!stream.isClosedLocally()) {
            sendData(stream);
        }
    }

    @Override
    public void readHeader(int streamId, Header header) {

        System.err.println("    " + header);
        SpdyStream stream = activeStreams.get(streamId);
        assert(stream != null); // Should have been caught when frame was decoded

        try {
            stream.onHeader(header);
        } catch (IOException e) {
            sendRstStream(streamId, SPDY_STREAM_PROTOCOL_ERROR);
            activeStreams.remove(stream);
            stream.close(e);
        }
    }

    @Override
    public void readHeadersEnd(int streamId) {

        System.err.println("end headers");
        SpdyStream stream = activeStreams.get(streamId);
        assert(stream != null); // Should have been caught when frame was decoded

        if (stream.isClosed()) {
            activeStreams.remove(stream);
            stream.complete();
        }
    }

    @Override
    public void readFrameSkipped(int streamId, String message) {

        SpdyStream stream = activeStreams.get(streamId);
        if (stream != null) {
            sendRstStream(streamId, SPDY_STREAM_PROTOCOL_ERROR);
            activeStreams.remove(stream);
            stream.close(new SpdyProtocolException(message));
        }
    }

    @Override
    public void readFrameError(String message) {
        closeWithError(new SpdyProtocolException(message));
    }

    public void sendSynStream(int streamId, byte priority, boolean last, Headers headers) {

        SpdyStream stream = activeStreams.get(streamId);
        assert(!stream.isClosedLocally());
        if (last) stream.closeLocally();

        WriteLogger logger = new WriteLogger(
            "sent SYN_STREAM (%d)\n--> Stream-ID = " + streamId + "\n--> Priority = " + priority +
            "\n--> Last = " + last
        );
        socket.write(frameEncoder.encodeSynStreamFrame(streamId, 0, priority, last, false, headers))
            .addListener(logger);
    }

    public void sendRstStream(int streamId, int streamStatus) {
        WriteLogger logger = new WriteLogger(
            "sent RST_STREAM (%d)\n--> Stream-ID = " + streamId + "\n--> Status = " + streamStatus);
        socket.write(frameEncoder.encodeRstStreamFrame(streamId, streamStatus)).addListener(logger);
    }

    public void sendWindowUpdate(int streamId, int delta) {
        WriteLogger logger = new WriteLogger(
            "sent WINDOW_UPDATE (%d)\n--> Stream-ID = " + streamId + "\n--> Delta = " + delta);
        socket.write(frameEncoder.encodeWindowUpdateFrame(streamId, delta)).addListener(logger);
    }

    private void sendData(SpdyStream stream) {

        int streamId = stream.getStreamId();
        int sendWindow = Math.min(sessionSendWindow, stream.getSendWindow());

        while (stream.hasPendingData()) {
            assert(!stream.isClosedLocally());
            if (sendWindow == 0) {
                // TODO: measure flow control delay here
                // stream.markBlocked();
                return;
            }
            // stream.markUnblocked();

            ByteBuffer data;
            try {
                data = stream.readData(sendWindow);
            } catch (IOException e) {
                sendRstStream(streamId, SPDY_STREAM_INTERNAL_ERROR);
                activeStreams.remove(stream);
                stream.close(e);
                return;
            }

            int bytesSent = data.remaining();
            boolean last = !stream.hasPendingData();
            if (bytesSent > 0 || last) {
                WriteLogger logger = new WriteLogger(
                    "sent DATA (%d)\n--> Stream-ID = " + streamId + "\n--> Last = " + last);
                socket.write(frameEncoder.encodeDataFrame(streamId, last, data))
                    .addListener(logger);

                sessionSendWindow -= bytesSent;
                stream.reduceSendWindow(bytesSent);
            }

            if (last) stream.closeLocally();
        }
    }

    private void sendClientSettings() {

        SpdySettings settings = new SpdySettings();
        settings.setValue(SpdySettings.MAX_CONCURRENT_STREAMS, 100);
        settings.setValue(SpdySettings.INITIAL_WINDOW_SIZE, initialReceiveWindow);

        WriteLogger logger = new WriteLogger("sent SETTINGS (%d)\n" + settings.toString());
        socket.write(frameEncoder.encodeSettingsFrame(settings)).addListener(logger);
    }

    private void sendPing() {

        final int pingId = nextPingId;
        nextPingId += 2;

        Socket.WriteFuture pingFuture = socket.write(frameEncoder.encodePingFrame(pingId));

        pingFuture.addListener(new Inline.Listener<Long>() {
            @Override
            public void onComplete(Long result) {
                sentPingMap.put(pingId, PlatformAdapter.instance().timestamp());
            }
        });

        pingFuture.addListener(new WriteLogger("sent PING (%d)\n--> Ping-ID = " + pingId));
    }

    private void sendPingResponse(int pingId) {
        WriteLogger logger = new WriteLogger("sent PING (%d)\n--> Ping-ID = " + pingId);
        socket.write(frameEncoder.encodePingFrame(pingId)).addListener(logger);
    }

    private void sendGoAway(int status) {

        if (sentGoAwayFrame) return;
        sentGoAwayFrame = true;

        Socket.WriteFuture goawayFuture = socket.write(
            frameEncoder.encodeGoAwayFrame(lastGoodStreamId, status));

        goawayFuture.addListener(new Inline.Listener<Long>() {
            @Override
            public void onComplete(Long result) {
                if (activeStreams.isEmpty()) {
                    socket.close();
                }
            }
        });
        goawayFuture.addListener(new WriteLogger(
            "sent GOAWAY (%d)\n--> Last-Good-Stream-ID = " + lastGoodStreamId +
                "\n--> Status: " + status
        ));
    }

    public void closeWithError(Throwable throwable) {

        error = true;
        Iterator<SpdyStream> i = activeStreams.iterator();
        while (i.hasNext()) {
            SpdyStream stream = i.next();
            i.remove();
            stream.close(throwable);
        }
        sendGoAway(SPDY_SESSION_PROTOCOL_ERROR);
        closeFuture.fail(throwable);
    }

    private class WriteLogger extends Inline.Listener<Long> {
        String message;

        WriteLogger(final String message) {
            this.message = message;
        }

        @Override
        public void onComplete(Long result) {
            System.err.println(String.format(message, result));
        }
    }

    private class SocketCloseListener extends Inline.Listener<Void> {

        /**
         * Occurs when the client has initiated the connection closure.
         */
        @Override
        public void onComplete(Void result) {
            // We should never attempt to close the socket if there are active streams.
            assert activeStreams.size() == 0;
            closeFuture.set(null);
        }

        /**
         * Occurs when the connection closes unexpectedly.
         * @param throwable the cause of the connection closure
         */
        @Override
        public void onError(Throwable throwable) {

            Iterator<SpdyStream> i = activeStreams.iterator();
            while (i.hasNext()) {
                SpdyStream stream = i.next();
                i.remove();
                stream.close(throwable);
            }
            closeFuture.fail(throwable);
        }
    }
}
