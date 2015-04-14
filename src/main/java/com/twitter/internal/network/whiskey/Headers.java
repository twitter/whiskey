package com.twitter.internal.network.whiskey;

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

    public static String ACCEPT = "accept";
    public static String ACCEPT_CHARSET = "accept-charset";
    public static String ACCEPT_LANGUAGE = "accept-language";
    public static String ACCEPT_ENCODING = "accept-encoding";
    public static String CONTENT_LENGTH = "content-length";
    public static String CONTENT_TYPE = "content-type";
    public static String COOKIE = "cookie";
    public static String DATE = "date";
    public static String SET_COOKIE = "set-cookie";

    public static String CONNECTION = "connection";
    public static String KEEP_ALIVE = "keep-alive";
    public static String PROXY_CONNECTION = "proxy-connection";
    public static String TRANSFER_ENCODING = "transfer-encoding";

    public static String[] RESERVED_HEADERS = { ":host", ":path", ":method", ":scheme" };

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
