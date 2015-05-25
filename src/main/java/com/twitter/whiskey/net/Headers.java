/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.net;

import com.twitter.whiskey.util.AbstractMultiMap;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;

/**
 * Multimap for managing HTTP headers.
 *
 * @author Michael Schore
 */
public class Headers extends AbstractMultiMap<String, String> {

    public static final String ACCEPT = "accept";
    public static final String ACCEPT_CHARSET = "accept-charset";
    public static final String ACCEPT_LANGUAGE = "accept-language";
    public static final String ACCEPT_ENCODING = "accept-encoding";
    public static final String CONTENT_LENGTH = "content-length";
    public static final String CONTENT_TYPE = "content-type";
    public static final String COOKIE = "cookie";
    public static final String DATE = "date";
    public static final String IF_MODIFIED_SINCE = "if-modified-since";

    public static final String CONTENT_ENCODING = "content-encoding";
    public static final String LAST_MODIFIED = "last-modified";
    public static final String LOCATION = "location";
    public static final String SET_COOKIE = "set-cookie";

    public static final String CONNECTION = "connection";
    public static final String KEEP_ALIVE = "keep-alive";
    public static final String PROXY_CONNECTION = "proxy-connection";
    public static final String TRANSFER_ENCODING = "transfer-encoding";

    public static final String[] RESERVED_HEADERS = { ":host", ":path", ":method", ":scheme" };

    private HeaderCollection entries = new HeaderCollection();

    public Headers() {
        map = new HashMap<>();
    }

    public Headers(Headers headers) {
        map = new HashMap<>(headers.keySet().size());
        putAll(headers);
    }

    public Headers(Collection<Header> headers) {
        map = new HashMap<>(headers.size());
        addAll(headers);
    }

    @Override
    public void put(String key, String value) {
        Header header = new Header(key, value);
        super.put(header.getKey(), header.getValue());
    }

    @Override
    protected Deque<String> constructDeque(int initialCapacity) {
        return new ArrayDeque<>(initialCapacity);
    }

    @Override
    public Collection<Header> entries() {
        return entries;
    }

    private class HeaderCollection extends EntryCollection<Header> {

        @Override
        protected Header constructEntry(String key, String value) {
            return new Header(key, value);
        }
    }
}
