package com.netflix.zuul.stats;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * An event that can be published to an <code>NFEventPublisher</code> and consumed by an <code>NFEventListener</code>,
 * defined by String key-value pairs.
 *
 * @author mhawthorne
 */
public class ZuulEvent {


    private static final Logger LOG = LoggerFactory.getLogger(ZuulEvent.class);


    private static final String TYPE_KEY = "type";

    private final JSONObject json;

    public ZuulEvent() {
        this(new JSONObject());
    }

    public ZuulEvent(JSONObject json) {
        this.json = json;
    }

    @Deprecated
    public ZuulEvent setAttribute(String key, String value) {
        return this.set(key, value);
    }

    public ZuulEvent set(String key, Object value) {
        try {
            this.json.put(key, value);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        return this;
    }

    public Object get(String key) {
        if(!this.has(key)) {
            return null;
        }

        try {
            return this.json.get(key);
        } catch (JSONException e) {
            LOG.debug(e.getMessage(),e);
            return null;
        }
    }

    public boolean has(String key) {
        return this.json.has(key);
    }

    public Iterator keys() {
        return this.json.keys();
    }

    public JSONObject toJson() {
        return this.json;
    }

    public Map<String, Object> toMap() {
        final Map<String, Object> m = new HashMap<String, Object>();
        for(final Iterator<String> i = this.keys(); i.hasNext();) {
            final String key = i.next();
            final Object val = this.get(key);
            if (val != null) m.put(key, val);
        }
        return m;
    }

    public Map<String, String> toStringMap() {
        final Map<String, String> m = new HashMap<String, String>();
        for(final Iterator<String> i = this.keys(); i.hasNext();) {
            final String key = i.next();
            final Object val = this.get(key);
            if (val != null) m.put(key, val.toString());
        }
        return m;
    }

    @Override
    public String toString() {
        return String.format("%s%s", this.getClass().getSimpleName(), this.json);
    }

}
