package com.twitter.whiskey.futures;


public interface ObservableFuture<T, E> extends StreamingFuture<T, E>, ListenableFuture<T>, Observable<E> {
}
