package com.twitter.internal.network.whiskey;


/**
 * @author Michael Schore
 */
public interface ResponseFuture extends ListenableFuture<Response> {
//    public void start();
    public Request getOriginalRequest();
    public Request getCurrentRequest();
    public ObservableFuture<Headers, Headers.Header> getHeadersFuture();
    public BodyFuture getBodyFuture();
//    public PushFuture getPushFuture();
    public StatsFuture getStatsFuture();
}
