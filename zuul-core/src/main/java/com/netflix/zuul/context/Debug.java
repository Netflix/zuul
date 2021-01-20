/*
 * Copyright 2018 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */
package com.netflix.zuul.context;

import com.netflix.zuul.message.Header;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.http.HttpRequestInfo;
import com.netflix.zuul.message.http.HttpResponseInfo;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

/**
 * Simple wrapper class around the RequestContext for setting and managing Request level Debug data.
 * @author Mikey Cohen
 * Date: 1/25/12
 * Time: 2:26 PM
 */
public class Debug {
    private static final Logger LOG = LoggerFactory.getLogger(Debug.class);

    public static void setDebugRequest(SessionContext ctx, boolean bDebug) {
        ctx.setDebugRequest(bDebug);
    }

    public static void setDebugRequestHeadersOnly(SessionContext ctx, boolean bHeadersOnly) {
        ctx.setDebugRequestHeadersOnly(bHeadersOnly);
    }

    public static boolean debugRequestHeadersOnly(SessionContext ctx) {
        return ctx.debugRequestHeadersOnly();
    }


    public static void setDebugRouting(SessionContext ctx, boolean bDebug) {
        ctx.setDebugRouting(bDebug);
    }


    public static boolean debugRequest(SessionContext ctx) {
        return ctx.debugRequest();
    }

    public static boolean debugRouting(SessionContext ctx) {
        return ctx.debugRouting();
    }

    public static void addRoutingDebug(SessionContext ctx, String line) {
        List<String> rd = getRoutingDebug(ctx);
        rd.add(line);
    }

    public static void addRequestDebugForMessage(SessionContext ctx, ZuulMessage message, String prefix)
    {
        for (Header header : message.getHeaders().entries()) {
            Debug.addRequestDebug(ctx, prefix + " " + header.getKey() + " " + header.getValue());
        }

        if (message.hasBody()) {
            String bodyStr = message.getBodyAsText();
            Debug.addRequestDebug(ctx, prefix + " " + bodyStr);
        }
    }

    /**
     *
     * @return Returns the list of routiong debug messages
     */
    public static List<String> getRoutingDebug(SessionContext ctx) {
        List<String> rd = (List<String>) ctx.get("routingDebug");
        if (rd == null) {
            rd = new ArrayList<String>();
            ctx.set("routingDebug", rd);
        }
        return rd;
    }

    /**
     * Adds a line to the  Request debug messages
     * @param line
     */
    public static void addRequestDebug(SessionContext ctx, String line) {
        List<String> rd = getRequestDebug(ctx);
        rd.add(line);
    }

    /**
     *
     * @return returns the list of request debug messages
     */
    public static List<String> getRequestDebug(SessionContext ctx) {
        List<String> rd = (List<String>) ctx.get("requestDebug");
        if (rd == null) {
            rd = new ArrayList<String>();
            ctx.set("requestDebug", rd);
        }
        return rd;
    }


    /**
     * Adds debug details about changes that a given filter made to the request context.
     * @param filterName
     * @param copy
     */
    public static void compareContextState(String filterName, SessionContext context, SessionContext copy) {
        // TODO - only comparing Attributes. Need to compare the messages too.

        // Ensure that the routingDebug property already exists, otherwise we'll have a ConcurrentModificationException below
        getRoutingDebug(context);

        Iterator<String> it = context.keySet().iterator();
        String key = it.next();
        while (key != null) {
            if ((!key.equals("routingDebug") && !key.equals("requestDebug"))) {
                Object newValue = context.get(key);
                Object oldValue = copy.get(key);
                if (oldValue == null && newValue != null) {
                    addRoutingDebug(context, "{" + filterName + "} added " + key + "=" + newValue.toString());
                } else if (oldValue != null && newValue != null) {
                    if (!(oldValue.equals(newValue))) {
                        addRoutingDebug(context, "{" +filterName + "} changed " + key + "=" + newValue.toString());
                    }
                }
            }
            if (it.hasNext()) {
                key = it.next();
            } else {
                key = null;
            }
        }

    }

    public static Observable<Boolean> writeDebugRequest(SessionContext context,
                                                                HttpRequestInfo request, boolean isInbound)
    {
        Observable<Boolean> obs = null;
        if (Debug.debugRequest(context)) {
            String prefix = isInbound ? "REQUEST_INBOUND" : "REQUEST_OUTBOUND";
            String arrow = ">";

            Debug.addRequestDebug(context, String.format("%s:: %s LINE: %s %s %s",
                    prefix, arrow, request.getMethod().toUpperCase(), request.getPathAndQuery(), request.getProtocol()));
            obs = Debug.writeDebugMessage(context, request, prefix, arrow);
        }

        if (obs == null)
            obs = Observable.just(Boolean.FALSE);

        return obs;
    }

    public static Observable<Boolean> writeDebugResponse(SessionContext context,
                                                                  HttpResponseInfo response, boolean isInbound)
    {
        Observable<Boolean> obs = null;
        if (Debug.debugRequest(context)) {
            String prefix = isInbound ? "RESPONSE_INBOUND" : "RESPONSE_OUTBOUND";
            String arrow = "<";

            Debug.addRequestDebug(context, String.format("%s:: %s STATUS: %s", prefix, arrow, response.getStatus()));
            obs = Debug.writeDebugMessage(context, response, prefix, arrow);
        }

        if (obs == null)
            obs = Observable.just(Boolean.FALSE);

        return obs;
    }

    public static Observable<Boolean> writeDebugMessage(SessionContext context, ZuulMessage msg,
                                                            String prefix, String arrow)
    {
        Observable<Boolean> obs = null;

        for (Header header : msg.getHeaders().entries()) {
            Debug.addRequestDebug(context, String.format("%s:: %s HDR: %s:%s", prefix, arrow, header.getKey(), header.getValue()));
        }

        // Capture the response body into a Byte array for later usage.
        if (msg.hasBody()) {
            if (! Debug.debugRequestHeadersOnly(context)) {
                // Convert body to a String and add to debug log.
                String body = msg.getBodyAsText();
                Debug.addRequestDebug(context, String.format("%s:: %s BODY: %s", prefix, arrow, body));
            }
        }

        if (obs == null)
            obs = Observable.just(Boolean.FALSE);

        return obs;
    }
}
