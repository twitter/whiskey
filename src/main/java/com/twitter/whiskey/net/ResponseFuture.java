/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.net;

import com.twitter.whiskey.futures.ListenableFuture;

/**
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
