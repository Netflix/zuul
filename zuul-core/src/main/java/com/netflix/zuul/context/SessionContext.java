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


import com.netflix.config.DynamicPropertyFactory;
import com.netflix.zuul.filters.FilterError;
import com.netflix.zuul.message.http.HttpResponseMessage;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the context between client and origin server for the duration of the dedicated connection/session
 * between them. But we're currently still only modelling single request/response pair per session.
 *
 * NOTE: Not threadsafe, and not intended to be used concurrently.
 *
 * User: Mike Smith
 * Date: 4/28/15
 * Time: 6:45 PM
 */
public final class SessionContext extends HashMap<String, Object> implements Cloneable {
    private static final int INITIAL_SIZE =
            DynamicPropertyFactory.getInstance().getIntProperty("com.netflix.zuul.context.SessionContext.initialSize", 60).get();

    private boolean brownoutMode = false;
    private boolean shouldStopFilterProcessing = false;
    private boolean shouldSendErrorResponse = false;
    private boolean errorResponseSent = false;
    private boolean debugRouting = false;
    private boolean debugRequest = false;
    private boolean debugRequestHeadersOnly = false;
    private boolean cancelled = false;

    private static final String KEY_UUID = "_uuid";
    private static final String KEY_VIP = "routeVIP";
    private static final String KEY_ENDPOINT = "_endpoint";
    private static final String KEY_STATIC_RESPONSE = "_static_response";

    private static final String KEY_EVENT_PROPS = "eventProperties";
    private static final String KEY_FILTER_ERRORS = "_filter_errors";
    private static final String KEY_FILTER_EXECS = "_filter_executions";

    public SessionContext()
    {
        // Use a higher than default initial capacity for the hashmap as we generally have more than the default
        // 16 entries.
        super(INITIAL_SIZE);

        put(KEY_FILTER_EXECS, new StringBuilder());
        put(KEY_EVENT_PROPS, new HashMap<String, Object>());
        put(KEY_FILTER_ERRORS, new ArrayList<FilterError>());
    }

    /**
     * Makes a copy of the RequestContext. This is used for debugging.
     */
    @Override
    public SessionContext clone()
    {
        return (SessionContext) super.clone();
    }

    public String getString(String key)
    {
        return (String) get(key);
    }

    /**
     * Convenience method to return a boolean value for a given key
     *
     * @return true or false depending what was set. default is false
     */
    public boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    /**
     * Convenience method to return a boolean value for a given key
     *
     * @return true or false depending what was set. default defaultResponse
     */
    public boolean getBoolean(String key, boolean defaultResponse) {
        Boolean b = (Boolean) get(key);
        if (b != null) {
            return b.booleanValue();
        }
        return defaultResponse;
    }

    /**
     * sets a key value to Boolean.TRUE
     */
    public void set(String key) {
        put(key, Boolean.TRUE);
    }

    /**
     * puts the key, value into the map. a null value will remove the key from the map
     *
     */
    public void set(String key, Object value) {
        if (value != null) put(key, value);
        else remove(key);
    }

    public String getUUID()
    {
        return getString(KEY_UUID);
    }
    public void setUUID(String uuid)
    {
        set(KEY_UUID, uuid);
    }

    public void setStaticResponse(HttpResponseMessage response) {
        set(KEY_STATIC_RESPONSE, response);
    }
    public HttpResponseMessage getStaticResponse() {
        return (HttpResponseMessage) get(KEY_STATIC_RESPONSE);
    }

    /**
     * Gets the throwable that will be use in the Error endpoint.
     *
     */
    public Throwable getError() {
        return (Throwable) get("_error");

    }

    /**
     * Sets throwable to use for generating a response in the Error endpoint.
     */
    public void setError(Throwable th) {
        put("_error", th);

    }

    public String getErrorEndpoint() {
        return (String) get("_error-endpoint");
    }
    public void setErrorEndpoint(String name) {
        put("_error-endpoint", name);
    }

    /**
     * sets  debugRouting
     */
    public void setDebugRouting(boolean bDebug) {
        this.debugRouting = bDebug;
    }

    /**
     * @return "debugRouting"
     */
    public boolean debugRouting() {
        return debugRouting;
    }

    /**
     * sets "debugRequestHeadersOnly" to bHeadersOnly
     *
     */
    public void setDebugRequestHeadersOnly(boolean bHeadersOnly) {
        this.debugRequestHeadersOnly = bHeadersOnly;
    }

    /**
     * @return "debugRequestHeadersOnly"
     */
    public boolean debugRequestHeadersOnly() {
        return this.debugRequestHeadersOnly;
    }

    /**
     * sets "debugRequest"
     */
    public void setDebugRequest(boolean bDebug) {
        this.debugRequest = bDebug;
    }

    /**
     * gets debugRequest
     *
     * @return debugRequest
     */
    public boolean debugRequest() {
        return this.debugRequest;
    }

    /**
     * removes "routeHost" key
     */
    public void removeRouteHost() {
        remove("routeHost");
    }

    /**
     * sets routeHost
     *
     * @param routeHost a URL
     */
    public void setRouteHost(URL routeHost) {
        set("routeHost", routeHost);
    }

    /**
     * @return "routeHost" URL
     */
    public URL getRouteHost() {
        return (URL) get("routeHost");
    }

    /**
     * appends filter name and status to the filter execution history for the
     * current request
     */
    public void addFilterExecutionSummary(String name, String status, long time) {
        StringBuilder sb = getFilterExecutionSummary();
        if (sb.length() > 0) sb.append(", ");
        sb.append(name).append('[').append(status).append(']').append('[').append(time).append("ms]");
    }

    /**
     * @return String that represents the filter execution history for the current request
     */
    public StringBuilder getFilterExecutionSummary() {
        return (StringBuilder) get(KEY_FILTER_EXECS);
    }

    public boolean shouldSendErrorResponse() {
        return this.shouldSendErrorResponse;
    }

    /**
     * Set this to true to indicate that the Error endpoint should be applied after
     * the end of the current filter processing phase.
     *
     */
    public void setShouldSendErrorResponse(boolean should) {
        this.shouldSendErrorResponse = should;
    }


    public boolean errorResponseSent() {
        return this.errorResponseSent;
    }

    public void setErrorResponseSent(boolean should) {
        this.errorResponseSent = should;
    }


    /**
     * This can be used by filters for flagging if the server is getting overloaded, and then choose
     * to disable/sample/rate-limit some optional features.
     *
     */
    public boolean isInBrownoutMode()
    {
        return brownoutMode;
    }

    public void setInBrownoutMode()
    {
        this.brownoutMode = true;
    }

    /**
     * This is typically set by a filter when wanting to reject a request, and also reduce load on the server
     * by not processing any subsequent filters for this request.
     */
    public void stopFilterProcessing() {
        shouldStopFilterProcessing = true;
    }

    public boolean shouldStopFilterProcessing() {
        return shouldStopFilterProcessing;
    }

    /**
     * returns the routeVIP; that is the Eureka "vip" of registered instances
     *
     */
    public String getRouteVIP() {
        return (String) get(KEY_VIP);
    }

    /**
     * sets routeVIP; that is the Eureka "vip" of registered instances
     */
    public void setRouteVIP(String sVip) {
        set(KEY_VIP, sVip);
    }

    public void setEndpoint(String endpoint)
    {
        put(KEY_ENDPOINT, endpoint);
    }

    public String getEndpoint()
    {
        return (String) get(KEY_ENDPOINT);
    }

    public void setEventProperty(String key, Object value) {
        getEventProperties().put(key, value);
    }

    public Map<String, Object> getEventProperties() {
        return (Map<String, Object>) this.get(KEY_EVENT_PROPS);
    }

    public List<FilterError> getFilterErrors() {
        return (List<FilterError>) get(KEY_FILTER_ERRORS);
    }

    public void setOriginReportedDuration(int duration)
    {
        put("_originReportedDuration", duration);
    }

    public int getOriginReportedDuration()
    {
        Object value = get("_originReportedDuration");
        if (value != null) {
            return (Integer) value;
        }
        return -1;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        this.cancelled = true;
    }
}