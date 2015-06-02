/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.net;

import com.twitter.whiskey.futures.ReactiveFuture;

/**
 * Internal implementation of a {@link HeadersFuture}.
 *
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
