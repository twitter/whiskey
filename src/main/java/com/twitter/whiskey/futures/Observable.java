package com.twitter.whiskey.futures;


/**
 * @author Michael Schore
 */
public interface Observable<E> {
    public void addObserver(Observer<E> observer);
}
