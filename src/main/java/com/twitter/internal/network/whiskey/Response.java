package com.twitter.internal.network.whiskey;


import java.nio.ByteBuffer;

/**
 * @author Michael Schore
 */
public class Response {
    private int statusCode;
    private Headers headers;
    private ByteBuffer body;
    private RequestStats stats;

    Response(int statusCode, Headers headers, ByteBuffer body, RequestStats stats) {
        this.statusCode = statusCode;
        this.headers = headers;
        this.body = body;
        this.stats = stats;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Headers getHeaders() {
        return headers;
    }

    public ByteBuffer getBody() { return body; }

    public RequestStats getStats() {
        return stats;
    }
}
