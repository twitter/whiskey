package com.twitter.internal.network.whiskey;

import android.support.annotation.Nullable;

import java.io.InputStream;
import java.net.CookieHandler;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

public class Request {
    private final URL url;
    private final Request.Method method;
    private final Headers headers;
    private final ByteBuffer[] bodyData;
    private final InputStream bodyStream;
    private final CookieHandler cookieHandler;
    private final TimeUnit timeoutUnit;
    private final TimeUnit discretionaryUnit;
    private final double priority;
    private final long discretionaryTimeout;
    private final long timeout;
    private final int maxRedirects;
    private final boolean idempotent;

    Request(
        URL url,
        Method method,
        Headers headers,
        ByteBuffer[] bodyData,
        InputStream bodyStream,
        double priority,
        CookieHandler cookieHandler,
        long discretionaryTimeout,
        TimeUnit discretionaryUnit,
        boolean idempotent,
        int maxRedirects,
        long timeout,
        TimeUnit timeoutUnit
    ) {
        this.url = url;
        this.method = method;
        this.headers = headers;
        this.bodyData = bodyData;
        this.bodyStream = bodyData == null ? bodyStream : null;
        this.priority = priority;
        this.cookieHandler = cookieHandler;
        this.discretionaryTimeout = discretionaryTimeout;
        this.discretionaryUnit = discretionaryUnit;
        this.idempotent = idempotent;
        this.maxRedirects = maxRedirects;
        this.timeout = timeout;
        this.timeoutUnit = timeoutUnit;
    }

    public URL getUrl() {
        return url;
    }

    public Method getMethod() {
        return method;
    }

    public Headers getHeaders() {
        return headers;
    }

    public ByteBuffer[] getBodyData() {
        return bodyData;
    }

    public InputStream getBodyStream() {
        return bodyStream;
    }

    public CookieHandler getCookieHandler() {
        return cookieHandler;
    }

    public int getMaxRedirects() {
        return maxRedirects;
    }

    public double getPriority() {
        return priority;
    }

    public long getTimeout() {
        return timeout;
    }

    public TimeUnit getTimeoutUnit() {
        return timeoutUnit;
    }

    /**
     * HTTP Request Method as specified in RFC 2616
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html
     */
    public static enum Method {
        //      (name, idempotent, bodySupported, cacheable)
        OPTIONS ("OPTIONS", true, true, false),
        GET     ("GET", true, false, true),
        HEAD    ("HEAD", true, false, false), // may invalidate existing cache entries
        POST    ("POST", false, true, true),
        PUT     ("PUT", true, true, false),
        DELETE  ("DELETE", true, false, false), // may invalidate existing cache entries
        TRACE   ("TRACE", true, true, false);

        private final String method;
        private final boolean idempotent;
        private final boolean bodySupported;
        private final boolean cacheable;

        Method(String method, boolean idempotent, boolean bodySupported, boolean cacheable) {
            this.method = method;
            this.idempotent = idempotent;
            this.bodySupported = bodySupported;
            this.cacheable = cacheable;
        }

        public String toString() {
            return method;
        }

        @Nullable
        public static Method fromString(String name) {
            Method method;
            try {
                method = valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                method = null;
            }
            return method;
        }

        public boolean isIdempotent() {
            return idempotent;
        }

        public boolean isBodySupported() {
            return bodySupported;
        }

        public boolean isCacheable() {
            return cacheable;
        }
    }

    public static class Builder {
        private URL url;
        private Request.Method method;
        private Headers headers;
        private ByteBuffer[] bodyData;
        private InputStream bodyStream;
        private CookieHandler cookieHandler;
        private TimeUnit timeoutUnit;
        private TimeUnit discretionaryUnit;
        private double priority;
        private long discretionaryTimeout;
        private long timeout;
        private int maxRedirects;
        private boolean idempotent;

        public Builder() {
            method = Request.Method.GET;
            headers = new Headers();
            priority = 0.5;
            timeout = 60;
            timeoutUnit = TimeUnit.SECONDS;
        }

        public Builder(URL url) {
            this();
            this.url = url;
        }

        public Builder(String url) throws MalformedURLException {
            this(new URL(url));
        }

        public Builder(Request request) {
            this.url = request.getUrl();
            this.method = request.getMethod();
            this.headers = request.getHeaders();
        }

        public Builder url(URL url) {
            this.url = url;
            return this;
        }

        public Builder url(String url) throws MalformedURLException {
            this.url = new URL(url);
            return this;
        }

        public Builder method(Request.Method method) {
            this.method = method;
            return this;
        }

        public Builder headers(Headers headers) {
            this.headers = headers;
            return this;
        }

        public Builder headers(Collection<Header> headers) {
            this.headers = new Headers(headers);
            this.headers.addAll(headers);
            this.headers.entries().addAll(headers);
            return this;
        }

        public Builder addHeader(Header header) {
            if (headers == null) headers = new Headers();
            this.headers.add(header);
            return this;
        }

        public Builder addHeader(String name, String value) {
            if (headers == null) headers = new Headers();
            this.headers.put(name, value);
            return this;
        }

        public Builder addHeaders(Headers headers) {
            if (this.headers == null) this.headers = new Headers();
            this.headers.putAll(headers);
            return this;
        }

        public Builder addHeaders(Collection<Header> headers) {
            if (this.headers == null) this.headers = new Headers();
            this.headers.addAll(headers);
            return this;
        }

        /**
         * Sets the request body to be read from the passed byte buffers and clears any content
         * set by a previous call to this method or {@link #body(InputStream)}.
         */
        public Builder body(ByteBuffer ... body) {
            bodyData = body.length > 0 ? body : null;
            bodyStream = null;
            return this;
        }

        /**
         * Sets the request body to be read from an input stream and clears any content
         * set by a previous call to this method or {@link #body(ByteBuffer...)}.
         *
         * Streams that may block are not fully-supported at this time, and the upload will
         * be truncated the first time a call to {@link InputStream#available()}
         * returns 0.
         *
         * Note the preferred approach for uploading a file is to use {@link ByteBuffer}(s) via
         * {@link java.nio.channels.FileChannel#map(java.nio.channels.FileChannel.MapMode, long, long)}.
         * This can result in significantly improved performance over a {@link java.io.FileInputStream}.
         *
         * @param body the input stream from which the request body will be read
         */
        public Builder body(InputStream body) {
            this.bodyStream = body;
            bodyData = null;
            return this;
        }

        public Builder priority(double priority) {
            this.priority = priority;
            return this;
        }

        public Builder cookieHandler(CookieHandler handler) {
            cookieHandler = handler;
            return this;
        }

        /**
         * Sets whether the request should be treated as idempotent (which may affect retransmission
         * attempts). Note that the RFC 2616 does define idempotency as a property of several request
         * methods, but in practice APIs are incosistent about adhering to this.
         *
         * @param idempotent whether the request may be treated as idempotent
         */
        public Builder idempotent(boolean idempotent) {
            this.idempotent = idempotent;
            return this;
        }

        public Builder maxRedirects(int maxRedirects) {
            this.maxRedirects = maxRedirects;
            return this;
        }

        public Builder timeout(long timeout, TimeUnit unit) {
            this.timeout = timeout;
            timeoutUnit = unit;
            return this;
        }

        public Builder discretionary(long timeout, TimeUnit unit) {
            discretionaryTimeout = timeout;
            discretionaryUnit = unit;
            return this;
        }

        public Request create() {
            return new Request(
                url,
                method,
                headers,
                bodyData,
                bodyStream,
                priority,
                cookieHandler,
                discretionaryTimeout,
                discretionaryUnit,
                idempotent,
                maxRedirects,
                timeout,
                timeoutUnit
            );
        }
    }
}
