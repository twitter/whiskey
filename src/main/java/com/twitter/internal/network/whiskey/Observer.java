package com.twitter.internal.network.whiskey;

import java.util.concurrent.Executor;

public interface Observer<E> {
    public void onNext(E element);
    public void onError(Throwable throwable);
    public Executor getExecutor();
}
