package com.netflix.zuul.stats;

import java.util.Date;

/**
* @author mhawthorne
*/
public final class EventFilterValue {

    private final String raw;
    private final Class compiled;
    private final Date date;

    public EventFilterValue(String raw, Class compiled) {
         this.raw = raw;
         this.compiled = compiled;
         this.date = new Date();
    }

    public Class getCompiled() {
        return compiled;
    }

    public String getRaw() {
        return raw;
    }

    public Date getDate() {
        return date;
    }

    public String toString() {
        return String.format("%s('%s', %s)", this.getClass().getSimpleName(), this.raw, this.date);
    }

}
