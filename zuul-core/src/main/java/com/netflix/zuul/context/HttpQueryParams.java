package com.netflix.zuul.context;

import com.google.common.collect.ArrayListMultimap;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * User: michaels
 * Date: 2/24/15
 * Time: 10:58 AM
 */
public class HttpQueryParams
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
        List<String> values = delegate.get(name);
        if (values != null) {
            if (values.size() > 0) {
                return values.get(0);
            }
        }
        return null;
    }

    /**
     * Replace any/all entries with this key, with this single entry.
     *
     * @param name
     * @param value
     */
    public void set(String name, String value)
    {
        delegate.removeAll(name);
        delegate.put(name,  value);
    }

    public void add(String name, String value)
    {
        delegate.put(name,  value);
    }

    public Collection<Map.Entry<String, String>> getEntries()
    {
        return delegate.entries();
    }
}
