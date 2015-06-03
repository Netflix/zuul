package com.netflix.zuul.context;

import com.google.common.collect.ArrayListMultimap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;

import static junit.framework.Assert.assertEquals;

/**
 * User: michaels
 * Date: 2/24/15
 * Time: 10:58 AM
 */
public class HttpQueryParams implements Cloneable
{
    private ArrayListMultimap<String, String> delegate = ArrayListMultimap.create();


    public static HttpQueryParams parse(String queryString)
    {
        HttpQueryParams queryParams = new HttpQueryParams();
        if (queryString == null) {
            return queryParams;
        }

        StringTokenizer st = new StringTokenizer(queryString, "&");
        int i;
        while (st.hasMoreTokens()) {
            String s = st.nextToken();
            i = s.indexOf("=");
            if (i > 0 && s.length() > i + 1) {
                String name = s.substring(0, i);
                String value = s.substring(i + 1);

                try {
                    name = URLDecoder.decode(name, "UTF-8");
                    value = URLDecoder.decode(value, "UTF-8");
                } catch (Exception e) {
                }
                try {

                } catch (Exception e) {
                }

                queryParams.add(name, value);
            }
        }

        return queryParams;
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
        List<String> values = delegate.get(name);
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
        delegate.removeAll(name);
        delegate.put(name,  value);
    }

    public void add(String name, String value)
    {
        delegate.put(name,  value);
    }

    public Collection<Map.Entry<String, String>> entries()
    {
        return delegate.entries();
    }

    public Set<String> keySet() {
        return delegate.keySet();
    }

    public String toEncodedString()
    {
        StringBuilder sb = new StringBuilder();
        try {
            for (Map.Entry<String, String> entry : entries()) {
                sb.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
                sb.append('=');
                sb.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
                sb.append('&');
            }
        } catch (UnsupportedEncodingException e) {
            // Won't happen.
            e.printStackTrace();
        }
        return sb.toString();
    }

    @Override
    protected Object clone()
    {
        HttpQueryParams copy = new HttpQueryParams();
        copy.delegate.putAll(this.delegate);
        return copy;
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit
    {
        @Test
        public void testMultiples()
        {
            HttpQueryParams qp = new HttpQueryParams();
            qp.add("k1", "v1");
            qp.add("k1", "v2");
            qp.add("k2", "v3");

            assertEquals("k1=v1&k1=v2&k2=v3&", qp.toEncodedString());
        }

        @Test
        public void testToEncodedString()
        {
            HttpQueryParams qp = new HttpQueryParams();
            qp.add("k'1", "v1&");
            assertEquals("k%271=v1%26&", qp.toEncodedString());

            qp = new HttpQueryParams();
            qp.add("k+", "\n");
            assertEquals("k%2B=%0A&", qp.toEncodedString());
        }
    }
}
