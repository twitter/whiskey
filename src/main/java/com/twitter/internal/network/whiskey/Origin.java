package com.twitter.internal.network.whiskey;

import java.net.URL;

public final class Origin {
    private final String host;
    private final String scheme;
    private final String originString;
    private final int port;

    public Origin(String scheme, String host) {
        this.scheme = scheme;
        this.host = host;
        this.port = getPortForScheme(scheme);
        this.originString = scheme + "://" + host + ":" + port;
    }

    public Origin(String scheme, String host, int port) {
        this.scheme = scheme;
        this.host = host;
        this.port = port == -1 ? getPortForScheme(scheme) : port;
        this.originString = scheme + "://" + host + ":" + this.port;
    }

    public Origin(URL url) {
        this(url.getProtocol(), url.getHost(), url.getPort());
    }

    private int getPortForScheme(String scheme) {
        if (scheme.equals("https")) {
            return 443;
        } else if (scheme.equals("http")) {
            return 80;
        } else {
            throw new AssertionError("unknown scheme: " + scheme);
        }
    }

    public String getHost() {
        return host;
    }

    public String getScheme() {
        return scheme;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return originString;
    }

    @Override
    public int hashCode() {
        return originString.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Origin && originString.equals(obj.toString());
    }
}
