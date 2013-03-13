package com.netflix.api.proxy.util;

/**
 * Created with IntelliJ IDEA.
 * User: mcohen
 * Date: 6/7/12
 * Time: 12:49 PM
 * To change this template use File | Settings | File Templates.
 */
public class Pair<E1,E2> {

    private E1 first;
    private E2 second;

    public Pair(E1 first, E2 second) {
        this.first = first;
        this.second = second;
    }

    public E1 first() {
        return first;
    }

    public void setFirst(E1 first) {
        this.first = first;
    }

    public E2 second() {
        return second;
    }

    public void setSecond(E2 second) {
        this.second = second;
    }

}
