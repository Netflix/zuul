package com.netflix.zuul.lifecycle;


import com.google.common.collect.ArrayListMultimap;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Wraps a ListMultimap and ensures all keys are lower-case as http headers are
 * case insensitive.
 *
 * User: michaels@netflix.com
 * Date: 2/20/15
 * Time: 3:13 PM
 */
public class Headers
{
    private ArrayListMultimap<String, String> delegate = ArrayListMultimap.create();

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
}
