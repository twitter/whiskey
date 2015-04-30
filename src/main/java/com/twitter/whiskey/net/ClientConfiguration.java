package com.twitter.whiskey.net;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import javax.net.ssl.SSLContext;

class ClientConfiguration {
    final private LinkedHashSet<Protocol> protocols;
    final private Protocol preferredProtocol;
    final private UpgradeStrategy upgradeStrategy;
    final private SSLContext sslContext;
    final private int connectTimeout;
    final private int compressionLevel;
    final private int maxPushStreams;
    final private int maxTcpConnections;
    final private int sessionReceiveWindow;
    final private int streamReceiveWindow;
    final private boolean tcpNoDelay;

    public ClientConfiguration(
        List<Protocol> protocols,
        UpgradeStrategy upgradeStrategy,
        SSLContext sslContext,
        int connectTimeout,
        int compressionLevel,
        int maxPushStreams,
        int maxTcpConnections,
        int sessionReceiveWindow,
        int streamReceiveWindow,
        boolean tcpNoDelay
    ) {
        this.protocols = new LinkedHashSet<>(protocols);
        preferredProtocol = protocols.get(0);
        this.upgradeStrategy = upgradeStrategy;
        this.sslContext = sslContext;
        this.connectTimeout = connectTimeout;
        this.compressionLevel = compressionLevel;
        this.maxPushStreams = maxPushStreams;
        this.maxTcpConnections = maxTcpConnections;
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

    public int getMaxTcpConnections() {
        return maxTcpConnections;
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

    public Protocol getPreferredProtocol() {
        return preferredProtocol;
    }

    public SSLContext getSslContext() {
        return sslContext;
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
        private List<Protocol> protocols;
        private UpgradeStrategy upgradeStrategy;
        private SSLContext sslContext;
        private int connectTimeout;
        private int compressionLevel;
        private int maxPushStreams;
        private int maxTcpConnections;
        private int sessionReceiveWindow;
        private int streamReceiveWindow;
        private boolean tcpNoDelay;

        public Builder() {

            protocols = new ArrayList<>(1);
            protocols.add(Protocol.SPDY_3_1);
            upgradeStrategy = UpgradeStrategy.DIRECT;
            connectTimeout = 60000;
            compressionLevel = 0;
            maxPushStreams = 0;
            maxTcpConnections = 1;
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

        /**
         * Sets the maximum number of concurrent connections to open to a given
         * origin.
         *
         * Note this may apply transiently to multiplexed protocols to achieve
         * more CWND for requests or open a new session as one is draining. If
         * different behavior is desired per-protocol, it will be necessary to
         * configure more than one {@link WhiskeyClient}.
         */
        public Builder maxTcpConnections(int maxTcpConnections) {
            this.maxTcpConnections = maxTcpConnections;
            return this;
        }

        public Builder protocols(Protocol... protocols) {
            this.protocols = Arrays.asList(protocols);
            return this;
        }

        public Builder protocols(List<Protocol> protocols) {
            this.protocols = protocols;
            return this;
        }

        public Builder sslContext(SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        public Builder sessionReceiveWindow(int sessionReceiveWindow) {
            this.sessionReceiveWindow = sessionReceiveWindow;
            return this;
        }

        public Builder streamReceiveWindow(int streamReceiveWindow) {
            this.streamReceiveWindow = streamReceiveWindow;
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
                sslContext,
                connectTimeout,
                compressionLevel,
                maxPushStreams,
                maxTcpConnections,
                sessionReceiveWindow,
                streamReceiveWindow,
                tcpNoDelay
            );
        }
    }
}
