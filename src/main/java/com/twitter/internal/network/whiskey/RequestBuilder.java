package com.twitter.internal.network.whiskey;

import java.io.InputStream;
import java.net.CookieHandler;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author Michael Schore
 */
public class RequestBuilder {
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

    public RequestBuilder() {
        method = Request.Method.GET;
        headers = new Headers();
    }

    public RequestBuilder(URL url) {
        this();
        this.url = url;
    }

    public RequestBuilder(String url) throws MalformedURLException {
        this(new URL(url));
    }

    public RequestBuilder(Request request) {
        this.url = request.getUrl();
        this.method = request.getMethod();
        this.headers = request.getHeaders();
    }

    public RequestBuilder url(URL url) {
        this.url = url;
        return this;
    }

    public RequestBuilder url(String url) throws MalformedURLException {
        this.url = new URL(url);
        return this;
    }

    public RequestBuilder method(Request.Method method) {
        this.method = method;
        return this;
    }

    public RequestBuilder headers(Headers headers) {
        this.headers = headers;
        return this;
    }

    public RequestBuilder headers(Collection<Header> headers) {
        this.headers = new Headers(headers);
        this.headers.addAll(headers);
        this.headers.entries().addAll(headers);
        return this;
    }

    public RequestBuilder addHeader(Header header) {
        if (headers == null) headers = new Headers();
        this.headers.add(header);
        return this;
    }

    public RequestBuilder addHeader(String name, String value) {
        if (headers == null) headers = new Headers();
        this.headers.put(name, value);
        return this;
    }

    public RequestBuilder addHeaders(Headers headers) {
        if (this.headers == null) this.headers = new Headers();
        this.headers.putAll(headers);
        return this;
    }

    public RequestBuilder addHeaders(Collection<Header> headers) {
        if (this.headers == null) this.headers = new Headers();
        this.headers.addAll(headers);
        return this;
    }

    /**
     * Sets the request body to be read from the passed byte buffers and clears any content
     * set by a previous call to this method or {@link #body(InputStream)}.
     */
    public RequestBuilder body(ByteBuffer ... body) {
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
    public RequestBuilder body(InputStream body) {
        this.bodyStream = body;
        bodyData = null;
        return this;
    }

    public RequestBuilder priority(double priority) {
        this.priority = priority;
        return this;
    }

    public RequestBuilder cookieHandler(CookieHandler handler) {
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
    public RequestBuilder idempotent(boolean idempotent) {
        this.idempotent = idempotent;
        return this;
    }

    public RequestBuilder maxRedirects(int maxRedirects) {
        this.maxRedirects = maxRedirects;
        return this;
    }

    public RequestBuilder timeout(long timeout, TimeUnit unit) {
        this.timeout = timeout;
        timeoutUnit = unit;
        return this;
    }

    public RequestBuilder discretionary(long timeout, TimeUnit unit) {
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
