package com.twitter.internal.network.whiskey;

import java.net.URL;

public class Origin {
    private String host;
    private String protocol;
    private String scheme;
    private String originString;
    private int port;

    public Origin(String scheme, String host) {
        this.scheme = scheme;
        this.host = host;

        if (scheme.equals("https")) {
            port = 443;
        } else if (scheme.equals("http")) {
            port = 80;
        }
        originString = scheme + "://" + host + ":" + port;
    }

    public Origin(String scheme, String host, int port) {
        this.scheme = scheme;
        this.host = host;
        this.port = port;
        originString = scheme + "://" + host + ":" + port;
    }

    public Origin(URL url) {
        this(url.getProtocol(), url.getHost(), url.getPort());
    }


    public String getHost() {
        return host;
    }

    public String getProtocol() {
        return protocol;
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
