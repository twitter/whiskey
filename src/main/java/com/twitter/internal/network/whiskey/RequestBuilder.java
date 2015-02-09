package com.twitter.internal.network.whiskey;

import java.net.CookieHandler;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.Collection;
import java.util.List;
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
    private ReadableByteChannel bodyChannel;
    private CookieHandler cookieHandler;
    private TimeUnit timeoutUnit;
    private TimeUnit discretionaryUnit;
    private double priority;
    private long discretionaryTimeout;
    private long timeout;
    private int maxRedirects;
    private boolean idempotent;
    private boolean bodyChannelMayBlock;

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

    public RequestBuilder headers(Collection<Headers.Header> headers) {
        this.headers = new Headers(headers);
        return this;
    }

    public RequestBuilder headers(Map<String, List<String>> headers) {
        this.headers = new Headers(headers);
        return this;
    }

    public RequestBuilder header(Headers.Header header) {
        this.headers.add(header);
        return this;
    }

    public RequestBuilder header(String name, String value) {
        this.headers.add(name, value);
        return this;
    }

    public RequestBuilder body(ByteBuffer body) {
        bodyData = new ByteBuffer[1];
        bodyData[0] = body;
        return this;
    }

    public RequestBuilder body(ByteBuffer[] body) {
        bodyData = body;
        return this;
    }

    /**
     * Sets the request body to be read from a {@link ReadableByteChannel}. Overrides any previous
     * source for the request body.
     *
     * If {@param mayBlock} is set to false, reads will be expected to never block excessively and
     * will be performed directly on the networking Thread. If set to true a new Thread
     * will be spawned to safely block on reads without impacting other I/O.
     *
     * @param body the channel from which the request body will be read
     * @param mayBlock whether the provided channel may ever block
     */
    public RequestBuilder body(ReadableByteChannel body, boolean mayBlock) {
        bodyData = null;
        bodyChannel = body;
        bodyChannelMayBlock = mayBlock;
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
                bodyChannel,
                bodyChannelMayBlock,
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
