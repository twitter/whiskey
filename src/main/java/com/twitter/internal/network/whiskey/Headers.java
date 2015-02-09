package com.twitter.internal.network.whiskey;

import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Case-insensitive multimap for managing HTTP headers, with utility operations and properties.
 *
 * @author Michael Schore
 */

public class Headers implements Map<String, String[]> {

//    public static String ACCEPT = "accept";
//    public static String ACCEPT_CHARSET = "accept-charset";
//    public static String ACCEPT_LANGUAGE = "accept-language";
    public static String ACCEPT_ENCODING = "accept-encoding";
    public static String CONTENT_LENGTH = "content-length";
    public static String CONTENT_TYPE = "content-type";
    public static String COOKIE = "cookie";
    public static String SET_COOKIE = "set-cookie";
    public static String[] RESERVED_HEADERS = { ":host", ":path", ":method", ":scheme" };

    private Map<String, List<String>> headers = new HashMap<>();
    private int size = 0;

    public Headers() {
    }

    public Headers(Map<String, List<String>> map) {

    }

    public Headers(Collection<Header> collection) {

    }

    public void add(String name, String value) {

        String key = name.toLowerCase();
        List<String> list = headers.get(key);

        if (list == null) {
            list = new ArrayList<>(name.equals(COOKIE) ? 10 : 1);
            headers.put(key, list);
        }

        list.add(value);
        size++;
    }

    public void add(Header header) {
        add(header.getKey(), header.getValue());
    }


    public void addAll(HashMap<String, String[]> headers) {

        for (String rawKey : headers.keySet()) {
            String key = rawKey.toLowerCase();
            List<String> existing = this.headers.get(key);

            if (existing == null) {
                this.headers.put(key, Arrays.asList(headers.get(rawKey)));
            } else {
                existing.addAll(Arrays.asList(headers.get(rawKey)));
            }
        }
    }

    @Override
    public void clear() {
        headers.clear();
    }

    public boolean contains(Header header) {
        List<String> entries = headers.get(header.getKey().toLowerCase());
        return entries != null && entries.contains(header.getValue());
    }

    public boolean contains(String name, String value) {
        return contains(new Header(name, value));
    }

    @Override
    public boolean containsKey(Object key) {
        if (!(key instanceof String)) {
            throw new IllegalArgumentException("Headers supports only String keys");
        }

        return headers.containsKey(((String)key).toLowerCase());
    }

    @Override
    public boolean containsValue(Object value) {
        throw new UnsupportedOperationException("Use {@link #contains} to test for the presence of a header name-value pair");
    }

    @NonNull
    @Override
    public Set<Map.Entry<String, String[]>> entrySet() {
        throw new UnsupportedOperationException("Use {@link #collection} for a collection of all header name-value pairs");
    }

    @Override
    public String[] get(Object key) {

        if (!(key instanceof String)) {
            throw new IllegalArgumentException("Headers supports only String keys");
        }

        List<String> entries = headers.get(((String)key).toLowerCase());
        if (entries == null) {
            return null;
        } else {
            return entries.toArray(new String[entries.size()]);
        }
    }

    public String get(String key, int index) {

        List<String> entries = headers.get(key);
        if (entries == null) {
            return null;
        }

        return entries.get(index);
    }

    public String getFirst(String key) {
        return get(key, 0);
    }

    @Override
    public boolean isEmpty() {
        return headers.isEmpty();
    }

    @NonNull
    @Override
    public Set<String> keySet() {
        return headers.keySet();
    }

    @Override
    public String[] put(String key, String[] value) {
        return new String[0];
    }

    @Override
    public void putAll(Map<? extends String, ? extends String[]> map) {

    }

    @Override
    public String[] remove(Object key) {

        if (!(key instanceof String)) {
            throw new IllegalArgumentException("Headers supports only String keys");
        }

        String stringKey = ((String) key).toLowerCase();
        List<String> removed = headers.remove(stringKey);
        if (removed == null) {
            return null;
        }

        return removed.toArray(new String[removed.size()]);
    }

    public Map.Entry<String, String> remove(Map.Entry<String, String> entry) {

        String key = entry.getKey().toLowerCase();
        List<String> entries = headers.get(key);
        if (entries == null) {
            return null;
        }

        if (entries.remove(entry.getValue())) {
            if (entries.isEmpty()) {
                headers.remove(key);
            }
            return entry;
        }

        return null;
    }

    @Override
    public int size() {
        return headers.size();
    }

    @NonNull
    @Override
    public Collection<String[]> values() {
        throw new UnsupportedOperationException("Use {@link #collection()} for an alternate view of all header name-value pairs");
    }

    @NonNull
    public Collection<Header> collection() {
        return new Collection<Header>() {

            @Override
            public boolean add(Header object) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean addAll(Collection<? extends Header> collection) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void clear() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean contains(Object object) {
                return object instanceof Header && Headers.this.contains((Header) object);
            }

            @Override
            public boolean containsAll(@NonNull Collection<?> collection) {

                for (Object object : collection) {
                    if (!(object instanceof Header && Headers.this.contains((Header) object))) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public boolean isEmpty() {
                return Headers.this.isEmpty();
            }

            @NonNull
            @Override
            public Iterator<Header> iterator() {
                return null;
            }

            @Override
            public boolean remove(Object object) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean removeAll(@NonNull Collection<?> collection) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean retainAll(@NonNull Collection<?> collection) {
                return false;
            }

            @Override
            public int size() {
                return 0;
            }

            @NonNull
            @Override
            public Object[] toArray() {
                return new Object[0];
            }

            @NonNull
            @Override
            public <T> T[] toArray(@NonNull T[] array) {
                return null;
            }
        };
    }

    public static class Header implements Map.Entry<String, String>
    {
        private String key;
        private String value;

        Header(String key, String value) {
            this.key = key.toLowerCase();
            this.value = value;
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public String getValue() {
            return value;
        }

        @Override
        public String setValue(String object) {
            String previous = value;
            value = object;
            return previous;
        }
    }
}
