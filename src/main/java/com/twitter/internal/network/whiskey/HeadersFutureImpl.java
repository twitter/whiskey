package com.twitter.internal.network.whiskey;

/**
 * @author Michael Schore
 */
public class HeadersFutureImpl extends ReactiveFuture<Headers, Headers.Header> implements HeadersFuture {

    private Headers headers;

    void reset() {
        headers = new Headers();
    }

    @Override
    void accumulate(Headers.Header element) {

        if (headers == null) {
            headers = new Headers();
        }
        headers.add(element);
    }

    @Override
    Iterable<Headers.Header> drain() {
        return headers.collection();
    }

    @Override
    void complete() {
        set(headers);
    }
}
