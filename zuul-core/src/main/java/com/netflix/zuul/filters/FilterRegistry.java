package com.netflix.zuul.filters;


import com.netflix.zuul.IZuulFilter;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author mhawthorne
 */
public class FilterRegistry {

    private static final FilterRegistry INSTANCE = new FilterRegistry();

    public static final FilterRegistry instance() {
        return INSTANCE;
    }

    private final ConcurrentHashMap<String, IZuulFilter> filters = new ConcurrentHashMap<String, IZuulFilter>();

    private FilterRegistry() {
    }

    public IZuulFilter remove(String key) {
        return this.filters.remove(key);
    }

    public IZuulFilter get(String key) {
        return this.filters.get(key);
    }

    public void put(String key, IZuulFilter filter) {
        this.filters.putIfAbsent(key, filter);
    }

    public int size() {
        return this.filters.size();
    }

    public Collection<IZuulFilter> getAllFilters() {
        return this.filters.values();
    }

}
