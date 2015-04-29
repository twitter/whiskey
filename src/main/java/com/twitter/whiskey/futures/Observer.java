package com.twitter.whiskey.futures;

import java.util.concurrent.Executor;

public interface Observer<E> {
    public void onComplete();
    public void onNext(E element);
    public void onError(Throwable throwable);
    public Executor getExecutor();
}
