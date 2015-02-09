package com.twitter.internal.network.whiskey;


public interface ObservableFuture<T, E> extends StreamingFuture<T, E>, ListenableFuture<T>, Observable<E> {
}
