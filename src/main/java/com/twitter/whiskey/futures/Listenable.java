package com.twitter.whiskey.futures;


/**
 * @author Michael Schore
 */
public interface Listenable<T> {
    public void addListener(Listener<T> listener);
}
