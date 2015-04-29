package com.twitter.whiskey.futures;

import java.util.concurrent.Future;

public interface StreamingFuture<T, E> extends Future<T>, Iterable<E> {
}
