package com.netflix.zuul;

public interface FilterStateFactory<T> {

    public T create();
    
}
