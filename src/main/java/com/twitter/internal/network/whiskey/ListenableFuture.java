package com.twitter.internal.network.whiskey;

import java.util.concurrent.Future;

/**
 * @author Michael Schore
 */
public interface ListenableFuture<T> extends Future<T>, Listenable<T> {
}
