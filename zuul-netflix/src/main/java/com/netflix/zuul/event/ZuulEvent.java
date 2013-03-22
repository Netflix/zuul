package com.netflix.zuul.event;

import java.util.EventObject;

/**
 * Created with IntelliJ IDEA.
 * User: mcohen
 * Date: 3/22/13
 * Time: 10:09 AM
 * To change this template use File | Settings | File Templates.
 */
public class ZuulEvent {
    String eventType;
    String eventMessage;


    public ZuulEvent( String eventType, String eventMessage) {
        this.eventMessage = eventMessage;
        this.eventType = eventType;
    }
}
