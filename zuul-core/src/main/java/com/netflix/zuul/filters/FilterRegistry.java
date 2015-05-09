package com.netflix.zuul.filters;


import javax.inject.Singleton;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author mhawthorne
 */
@Singleton
public class FilterRegistry
{
    private final ConcurrentHashMap<String, ZuulFilter> filters = new ConcurrentHashMap<String, ZuulFilter>();

    public ZuulFilter remove(String key) {
        return this.filters.remove(key);
    }

    public ZuulFilter get(String key) {
        return this.filters.get(key);
    }

    public void put(String key, ZuulFilter filter) {
        this.filters.putIfAbsent(key, filter);
    }

    public int size() {
        return this.filters.size();
    }

    public Collection<ZuulFilter> getAllFilters() {
        return this.filters.values();
    }
}
