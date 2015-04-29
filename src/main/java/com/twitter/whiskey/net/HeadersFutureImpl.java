package com.twitter.whiskey.net;

import com.twitter.whiskey.futures.ReactiveFuture;

/**
 * @author Michael Schore
 */
public class HeadersFutureImpl extends ReactiveFuture<Headers, Header> implements HeadersFuture {

    private Headers headers;

    void reset() {
        headers = new Headers();
    }

    @Override
    protected void accumulate(Header element) {

        if (headers == null) {
            headers = new Headers();
        }
        headers.add(element);
    }

    @Override
    protected Iterable<Header> drain() {
        return headers.entries();
    }

    @Override
    protected boolean complete() {
        return set(headers);
    }
}
