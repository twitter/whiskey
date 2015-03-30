package com.twitter.internal.network.whiskey;

/**
 * @author Michael Schore
 */
public class HeadersFutureImpl extends ReactiveFuture<Headers, Header> implements HeadersFuture {

    private Headers headers;

    void reset() {
        headers = new Headers();
    }

    @Override
    void accumulate(Header element) {

        if (headers == null) {
            headers = new Headers();
        }
        headers.add(element);
    }

    @Override
    Iterable<Header> drain() {
        return headers.entries();
    }

    @Override
    boolean complete() {
        return set(headers);
    }
}
