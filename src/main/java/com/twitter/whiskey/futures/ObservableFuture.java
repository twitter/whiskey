/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.futures;

/**
 * @author Michael Schore
 * @param <T> the resulting type that this Future wraps
 * @param <E> units of progress or components of a final result produced
 *            prior to the Future's completion
 */
public interface ObservableFuture<T, E> extends StreamingFuture<T, E>, ListenableFuture<T>, Observable<E> {
}
