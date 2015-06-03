/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.net;

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
