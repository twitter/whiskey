package com.twitter.internal.network.whiskey;


/**
 * @author Michael Schore
 */
public interface Observable<E> {
    public void addObserver(Observer<E> observer);
}
