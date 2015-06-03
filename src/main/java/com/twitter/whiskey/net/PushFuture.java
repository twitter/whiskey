/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.net;

import com.twitter.whiskey.futures.ReactiveFuture;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Michael Schore
 */
public class PushFuture extends ReactiveFuture<Void, ResponseFuture> {

    private List<ResponseFuture> pushFutures = new LinkedList<>();

    @Override
    protected void accumulate(ResponseFuture element) {
        pushFutures.add(element);
    }

    @Override
    protected Iterable<ResponseFuture> drain() {

        List<ResponseFuture> drained = pushFutures;
        pushFutures = null;
        return drained;
    }

    @Override
    protected boolean complete() {
        return set(null);
    }
}
