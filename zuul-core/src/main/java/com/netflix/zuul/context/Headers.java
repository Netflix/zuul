package com.netflix.zuul.context;


import com.google.common.collect.ArrayListMultimap;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wraps a ListMultimap and ensures all keys are lower-case as http headers are
 * case insensitive.
 *
 * User: michaels@netflix.com
 * Date: 2/20/15
 * Time: 3:13 PM
 */
public class Headers implements Cloneable
{
    private final ArrayListMultimap<String, String> delegate;

    public Headers()
    {
        delegate = ArrayListMultimap.create();
    }

    private Headers(ArrayListMultimap<String, String> delegate)
    {
        this.delegate = delegate;
    }

    /**
     * Get the first value found for this key even if there are multiple. If none, then
     * return null.
     *
     * @param name
     * @return
     */
    public String getFirst(String name)
    {
        List<String> values = delegate.get(name.toLowerCase());
        if (values != null) {
            if (values.size() > 0) {
                return values.get(0);
            }
        }
        return null;
    }

    /**
     * Get the first value found for this key even if there are multiple. If none, then
     * return the specified defaultValue.
     *
     * @param name
     * @return
     */
    public String getFirst(String name, String defaultValue)
    {
        String value = getFirst(name);
        if (value == null) {
            value = defaultValue;
        }
        return value;
    }

    public List<String> get(String name)
    {
        return delegate.get(name.toLowerCase());
    }

    /**
     * Replace any/all entries with this key, with this single entry.
     *
     * @param name
     * @param value
     */
    public void set(String name, String value)
    {
        String lc_name = name.toLowerCase();
        delegate.removeAll(lc_name);
        delegate.put(lc_name,  value);
    }

    public void add(String name, String value)
    {
        delegate.put(name.toLowerCase(),  value);
    }

    public void remove(String name)
    {
        delegate.removeAll(name.toLowerCase());
    }

    public Collection<Map.Entry<String, String>> entries()
    {
        return delegate.entries();
    }

    public Set<String> keySet() {
        return delegate.keySet();
    }

    public boolean contains(String name, String value)
    {
        return delegate.containsEntry(name.toLowerCase(), value);
    }

    @Override
    protected Object clone()
    {
        return new Headers(ArrayListMultimap.create(this.delegate));
    }
}
