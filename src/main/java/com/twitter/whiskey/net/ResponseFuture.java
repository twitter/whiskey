/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.net;

import com.twitter.whiskey.futures.ListenableFuture;
import com.twitter.whiskey.futures.Observer;

import java.util.Iterator;

/**
 * {@link ListenableFuture} wrapping an HTTP response. Also provides access to
 * {@link com.twitter.whiskey.futures.StreamingFuture}s representing both the
 * {@link Headers} and body of the eventual response, allowing for flexible
 * interaction with the in-flight operation. All methods are thread-safe and
 * may be called immediately upon receipt of the object.
 *
 * @author Michael Schore
 */
public interface ResponseFuture extends ListenableFuture<Response> {

    /**
     * @return the original {@link Request} submitted to {@link WhiskeyClient}
     */
    public Request getOriginalRequest();

    /**
     * The current in-flight request may differ from the original due to redirects or
     * protocol-specific behavior.
     *
     * @return the most recent in-flight {@link Request}
     */
    public Request getCurrentRequest();

    /**
     * Add an observer for ResponseFutures representing pushed server content.
     * (See the SPDY draft spec or RFC 7540 for details on pushed content.)
     * {@link Observer#onNext)} is called with a new ResponseFuture as early
     * as feasible, to allow the client to potentially cancel the push or
     * stream the content.
     *
     * Note that only the first observer added via this method OR the first
     * iterator returned by {@link #pushIterator} is guaranteed to cover all
     * pushed content for a given request.
     */
    public void addPushObserver(Observer<ResponseFuture> observer);

    /**
     * Returns a blocking iterator of ResponseFutures representing pushed
     * server content. (See the SPDY draft spec or RFC 7540 for details on
     * pushed content.)
     *
     * Note that although calls to {@link Iterator#hasNext}
     * may block, the ResponseFutures returned by {@link Iterator#next}
     * still likely represent in-flight content thay may be cancelled or
     * streamed.
     *
     * Note that only the first iterator returned by this method OR the first
     * observer added via {@link #addPushObserver} is guaranteed to cover all
     * pushed content for a given request.
     */
    public Iterator<ResponseFuture> pushIterator();

    /**
     * @return a future representation of the final response's headers
     */
    public HeadersFuture getHeadersFuture();

    /**
     * @return a future representation of the final response's body
     */
    public BodyFuture getBodyFuture();

    /**
     * @return a future representation of metrics on the operation's performance
     */
    public StatsFuture getStatsFuture();
}
