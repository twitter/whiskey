package com.twitter.internal.network.whiskey;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

class ClientConfiguration {
    final private LinkedHashSet<Request.Protocol> protocols;
    final private Request.Protocol preferredProtocol;
    final private UpgradeStrategy upgradeStrategy;
    final private int connectTimeout;
    final private int compressionLevel;
    final private int maxPushStreams;
    final private int sessionReceiveWindow;
    final private int streamReceiveWindow;
    final private boolean tcpNoDelay;

    public ClientConfiguration(
        List<Request.Protocol> protocols,
        UpgradeStrategy upgradeStrategy,
        int connectTimeout,
        int compressionLevel,
        int maxPushStreams,
        int sessionReceiveWindow,
        int streamReceiveWindow,
        boolean tcpNoDelay
    ) {
        this.protocols = new LinkedHashSet<>(protocols);
        this.preferredProtocol = protocols.get(0);
        this.upgradeStrategy = upgradeStrategy;
        this.connectTimeout = connectTimeout;
        this.compressionLevel = compressionLevel;
        this.maxPushStreams = maxPushStreams;
        this.sessionReceiveWindow = sessionReceiveWindow;
        this.streamReceiveWindow = streamReceiveWindow;
        this.tcpNoDelay = tcpNoDelay;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public int getCompressionLevel() {
        return compressionLevel;
    }

    public int getMaxPushStreams() {
        return maxPushStreams;
    }

    public int getSessionReceiveWindow() {
        return sessionReceiveWindow;
    }

    public int getStreamReceiveWindow() {
        return streamReceiveWindow;
    }

    public boolean useTcpNoDelay() {
        return tcpNoDelay;
    }

    public UpgradeStrategy getUpgradeStrategy() {
        return upgradeStrategy;
    }

    public Request.Protocol getPreferredProtocol() {
        return preferredProtocol;
    }

    /**
     * The upgrade strategy to use when negotiating the protocol for a connection.
     *
     * DIRECT - No negotiation will be performed and SPDY will be used directly
     * on the wire. This is the only supported option for a non-TLS connection.
     *
     * NPN - Next Protocol Negotiation, a standard proposed by Google.
     * http://technotes.googlecode.com/git/nextprotoneg.html
     *
     * ALPN - A successor to NPN and the standard adopted for HTTP2.
     * https://tools.ietf.org/html/rfc7301
     */
    public static enum UpgradeStrategy {
        DIRECT,
        NPN,
        ALPN
    }

    public static class Builder {
        private List<Request.Protocol> protocols;
        private UpgradeStrategy upgradeStrategy;
        private int connectTimeout;
        private int compressionLevel;
        private int maxPushStreams;
        private int sessionReceiveWindow;
        private int streamReceiveWindow;
        private boolean tcpNoDelay;

        public Builder() {
            upgradeStrategy = UpgradeStrategy.ALPN;
            connectTimeout = 60000;
            compressionLevel = 0;
            sessionReceiveWindow = 10485760;
            streamReceiveWindow = 10485760;
            tcpNoDelay = false;
        }

        public Builder connectTimeout(int connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder compressionLevel(int compressionLevel) {
            this.compressionLevel = compressionLevel;
            return this;
        }

        public Builder maxPushStreams(int maxPushStreams) {
            this.maxPushStreams = maxPushStreams;
            return this;
        }

        public Builder protocols(Request.Protocol... protocols) {
            this.protocols = Arrays.asList(protocols);
            return this;
        }

        public Builder protocols(List<Request.Protocol> protocols) {
            this.protocols = protocols;
            return this;
        }
        public Builder sessionReceiveWindow(int sessionReceiveWindow) {
            this.sessionReceiveWindow = sessionReceiveWindow;
            return this;
        }

        public Builder tcpNoDelay(boolean tcpNoDelay) {
            this.tcpNoDelay = tcpNoDelay;
            return this;
        }

        public Builder upgradeStrategy(UpgradeStrategy upgradeStrategy) {
            this.upgradeStrategy = upgradeStrategy;
            return this;
        }

        public ClientConfiguration create() {
            return new ClientConfiguration(
                protocols,
                upgradeStrategy,
                connectTimeout,
                compressionLevel,
                maxPushStreams,
                sessionReceiveWindow,
                streamReceiveWindow,
                tcpNoDelay
            );
        }
    }
}
