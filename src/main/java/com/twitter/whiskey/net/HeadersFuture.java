/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.net;

import com.twitter.whiskey.futures.ObservableFuture;

/**
 * Future representing the headers of an HTTP response.
 *
 * @author Michael Schore
 */
public interface HeadersFuture extends ObservableFuture<Headers, Header> {
}
