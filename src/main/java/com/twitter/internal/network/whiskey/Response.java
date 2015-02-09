package com.twitter.internal.network.whiskey;


/**
 * @author Michael Schore
 */
public class Response {
    private int statusCode;
    private Headers headers;
    private RequestStats stats;

    Response(int statusCode, Headers headers, RequestStats stats) {
        this.statusCode = statusCode;
        this.headers = headers;
        this.stats = stats;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Headers getHeaders() {
        return headers;
    }

    public RequestStats getStatus() {
        return stats;
    }
}
