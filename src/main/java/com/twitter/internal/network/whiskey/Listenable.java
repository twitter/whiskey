package com.twitter.internal.network.whiskey;


/**
 * @author Michael Schore
 */
public interface Listenable<T> {
    public void addListener(Listener<T> listener);
}
