/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.net;

import com.twitter.whiskey.futures.ListenableFuture;

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
     * @return a future representation of the final response's headers
     */
    public HeadersFuture getHeadersFuture();

    /**
     * @return a future representation of the final response's body
     */
    public BodyFuture getBodyFuture();

//    public PushFuture getPushFuture();

    /**
     * @return a future representation of metrics on the operation's performance
     */
    public StatsFuture getStatsFuture();
}
