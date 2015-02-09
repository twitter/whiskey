package com.twitter.internal.network.whiskey;

import java.util.concurrent.Future;

public interface StreamingFuture<T, E> extends Future<T>, Iterable<E> {
}
