package com.twitter.internal.network.whiskey;

import android.support.annotation.Nullable;

/**
 * HTTP-compatible protocol.
 *
 * @author Michael Schore
 */
public enum Protocol {
    HTTP_1_0 ("http/1.0"),
    HTTP_1_1 ("http/1.1"),
    HTTP_2_0 ("http/2"),
    SPDY_2   ("spdy/2"),
    SPDY_3   ("spdy/3"),
    SPDY_3_1 ("spdy/3.1"),
    QUIC     ("quic");

    private final String name;

    private Protocol(String name) {
        this.name = name;
    }

    @Override public String toString() { return name; }

    @Nullable
    public static Protocol fromString(String name) {
        Protocol protocol;
        try {
            protocol = valueOf(name.toUpperCase().replaceAll("[-/.]", "_"));
        } catch (IllegalArgumentException e) {
            protocol = null;
        }
        return protocol;
    }
}
