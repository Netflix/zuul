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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.zuul.filters.FilterError;
import com.netflix.zuul.message.http.HttpResponseMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Represents the context between client and origin server for the duration of the dedicated connection/session
 * between them. But we're currently still only modeling single request/response pair per session.
 *
 * NOTE: Not threadsafe, and not intended to be used concurrently.
 *
 * User: Mike Smith
 * Date: 4/28/15
 * Time: 6:45 PM
 */
@NullMarked
public final class SessionContext implements Cloneable {
    private static final int INITIAL_SIZE = DynamicPropertyFactory.getInstance()
            .getIntProperty("com.netflix.zuul.context.SessionContext.initialSize", 60)
            .get();

    private static final int EVENT_PROPERTIES_INITIAL_SIZE = DynamicPropertyFactory.getInstance()
            .getIntProperty("com.netflix.zuul.context.SessionContext.eventProperties.initialSize", 128)
            .get();

    private static final SessionContext.Key<String> KEY_UUID = SessionContext.newKey("_uuid");
    private static final SessionContext.Key<String> KEY_VIP = SessionContext.newKey("routeVIP");
    private static final SessionContext.Key<String> KEY_ENDPOINT = SessionContext.newKey("_endpoint");
    private static final SessionContext.Key<HttpResponseMessage> KEY_STATIC_RESPONSE =
            SessionContext.newKey("_static_response");
    private static final SessionContext.Key<Throwable> KEY_ERROR = SessionContext.newKey("_error");
    private static final SessionContext.Key<String> KEY_ERROR_ENDPOINT = SessionContext.newKey("_error-endpoint");
    private static final SessionContext.Key<Integer> KEY_ORIGIN_REPORTED_DURATION =
            SessionContext.newKey("_originReportedDuration");

    private boolean brownoutMode = false;
    private boolean shouldStopFilterProcessing = false;
    private boolean shouldSendErrorResponse = false;
    private boolean errorResponseSent = false;
    private boolean cancelled = false;

    private final Map<String, Object> map;
    private final IdentityHashMap<Key<?>, Object> typedMap;
    private final StringBuilder filterExecutionSummary;
    private final Map<String, Object> eventProperties;
    private final List<FilterError> filterErrors;

    /**
     * A Key is type-safe, identity-based key into the Session Context.
     * @param <T>
     */
    public static final class Key<T> {

        private final String name;

        @Nullable
        private final Supplier<T> defaultValueSupplier;

        private Key(String name, @Nullable Supplier<T> defaultValueSupplier) {
            this.name = Objects.requireNonNull(name, "name");
            this.defaultValueSupplier = defaultValueSupplier;
        }

        @Override
        public String toString() {
            return "Key{" + name + '}';
        }

        public String name() {
            return name;
        }

        /**
         * This method exists solely to indicate that Keys are based on identity and not name.
         */
        @Override
        public boolean equals(@Nullable Object o) {
            return super.equals(o);
        }

        /**
         * This method exists solely to indicate that Keys are based on identity and not name.
         */
        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Nullable
        public T defaultValue() {
            return defaultValueSupplier != null ? defaultValueSupplier.get() : null;
        }
    }

    public SessionContext() {
        this(INITIAL_SIZE, EVENT_PROPERTIES_INITIAL_SIZE);
    }

    public SessionContext(int initialMapSize, int initialEventPropertiesSize) {
        this.map = new HashMap<>(initialMapSize);
        this.typedMap = new IdentityHashMap<>(initialMapSize);
        this.filterExecutionSummary = new StringBuilder();
        this.eventProperties = new HashMap<>(initialEventPropertiesSize);
        this.filterErrors = new ArrayList<>();
    }

    public static <T> Key<T> newKey(String name) {
        return newKey(name, null);
    }

    public static <T> Key<T> newKey(String name, @Nullable Supplier<T> defaultValueSupplier) {
        return new Key<>(name, defaultValueSupplier);
    }

    /**
     * Returns the value for the given string key, or {@code null} if absent.
     */
    @Nullable
    public Object get(String key) {
        return map.get(key);
    }

    /**
     * Returns the value in the context, or {@code null} if absent.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T get(Key<T> key) {
        T value = (T) typedMap.get(key);
        if (value == null) {
            value = key.defaultValue();
        }

        return value;
    }

    /**
     * Returns the value in the context, or default value from the
     * typed key default value supplier if absent.
     */
    public <T> T getOrDefault(Key<T> key) {
        return Objects.requireNonNull(this.get(key), "expected non-null value or defaultValue supplier");
    }

    /**
     * Returns the value in the context, or {@code defaultValue} if absent.
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(Key<T> key, T defaultValue) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(defaultValue, "defaultValue");
        T value = (T) typedMap.get(Objects.requireNonNull(key));
        if (value != null) {
            return value;
        }
        return defaultValue;
    }

    /**
     * Returns the value for the given string key, or {@code defaultValue} if absent.
     */
    public Object getOrDefault(String key, Object defaultValue) {
        return map.getOrDefault(key, defaultValue);
    }

    /**
     * Checks for the existence of the string key in the context.
     */
    public boolean containsKey(String key) {
        return map.containsKey(key);
    }

    /**
     * Checks for the existence of the key in the context.
     */
    public <T> boolean containsKey(Key<T> key) {
        return typedMap.containsKey(Objects.requireNonNull(key, "key"));
    }

    /**
     * Associates the value with the given string key, returning the previous value or {@code null}.
     */
    @Nullable
    public Object put(String key, Object value) {
        return map.put(key, value);
    }

    /**
     * Returns the previous value associated with key, or {@code null} if there was no mapping for key.  Unlike
     * {@link #put(String, Object)}, this will never return a null value if the key is present in the map.
     */
    @Nullable
    @CanIgnoreReturnValue
    public <T> T put(Key<T> key, T value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");

        @SuppressWarnings("unchecked")
        T res = (T) typedMap.put(key, value);
        return res;
    }

    /**
     * Removes the entry for the given string key only if it is currently mapped to the value.
     */
    public boolean remove(String key, Object value) {
        return map.remove(key, value);
    }

    public <T> boolean remove(Key<T> key, T value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        return typedMap.remove(key, value);
    }

    /**
     * Removes the entry for the given string key, returning the previous value or {@code null}.
     */
    @Nullable
    public Object remove(String key) {
        return map.remove(key);
    }

    @Nullable
    public <T> T remove(Key<T> key) {
        Objects.requireNonNull(key, "key");
        @SuppressWarnings("unchecked")
        T res = (T) typedMap.remove(key);
        return res;
    }

    public Set<Key<?>> keys() {
        return Set.copyOf(typedMap.keySet());
    }

    public int size() {
        return map.size() + typedMap.size();
    }

    /**
     * Makes a shallow copy of the SessionContext.
     */
    @Override
    public SessionContext clone() {
        SessionContext copy = new SessionContext();
        copy.map.putAll(this.map);
        copy.typedMap.putAll(this.typedMap);
        copy.filterExecutionSummary.append(this.filterExecutionSummary);
        copy.eventProperties.putAll(this.eventProperties);
        copy.filterErrors.addAll(this.filterErrors);
        copy.brownoutMode = brownoutMode;
        copy.shouldStopFilterProcessing = shouldStopFilterProcessing;
        copy.shouldSendErrorResponse = shouldSendErrorResponse;
        copy.errorResponseSent = errorResponseSent;
        copy.cancelled = cancelled;
        return copy;
    }

    @Nullable
    public String getString(String key) {
        return (String) get(key);
    }

    /**
     * Convenience method to return a boolean value for a given key
     *
     * @return true or false depending on what was set. default is false
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
            return b;
        }
        return defaultResponse;
    }

    /**
     * sets a key value to true
     */
    public void set(String key) {
        put(key, true);
    }

    /**
     * puts the key, value into the map. a null value will remove the key from the map
     *
     */
    public void set(String key, @Nullable Object value) {
        if (value != null) {
            put(key, value);
        } else {
            remove(key);
        }
    }

    /**
     * Puts the key, value into the context. A null value removes the key from the map.
     */
    public <T> void set(Key<T> key, @Nullable T value) {
        if (value != null) {
            put(key, value);
        } else {
            remove(key);
        }
    }

    @Nullable
    public String getUUID() {
        return get(KEY_UUID);
    }

    public void setUUID(String uuid) {
        set(KEY_UUID, uuid);
    }

    public void setStaticResponse(HttpResponseMessage response) {
        set(KEY_STATIC_RESPONSE, response);
    }

    @Nullable
    public HttpResponseMessage getStaticResponse() {
        return get(KEY_STATIC_RESPONSE);
    }

    /**
     * Gets the throwable that will be use in the Error endpoint.
     *
     */
    @Nullable
    public Throwable getError() {
        return get(KEY_ERROR);
    }

    /**
     * Sets throwable to use for generating a response in the Error endpoint. A null throwable clears any existing
     * error.
     */
    public void setError(@Nullable Throwable th) {
        set(KEY_ERROR, th);
    }

    @Nullable
    public String getErrorEndpoint() {
        return get(KEY_ERROR_ENDPOINT);
    }

    public void setErrorEndpoint(@Nullable String name) {
        set(KEY_ERROR_ENDPOINT, name);
    }

    /**
     * appends filter name and status to the filter execution history for the
     * current request
     */
    public void addFilterExecutionSummary(String name, String status, long time) {
        StringBuilder sb = getFilterExecutionSummary();
        if (!sb.isEmpty()) {
            sb.append(", ");
        }
        sb.append(name)
                .append('[')
                .append(status)
                .append(']')
                .append('[')
                .append(time)
                .append("ms]");
    }

    /**
     * @return String that represents the filter execution history for the current request
     */
    public StringBuilder getFilterExecutionSummary() {
        return filterExecutionSummary;
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
    public boolean isInBrownoutMode() {
        return brownoutMode;
    }

    /**
     * Flag the server is getting overloaded.
     * @deprecated use setInBrownoutMode(String reason)
     */
    @Deprecated
    public void setInBrownoutMode() {
        this.brownoutMode = true;
    }

    public void setInBrownoutMode(String reason) {
        this.brownoutMode = true;
        put(CommonContextKeys.BROWNOUT_REASON, reason);
    }

    @Nullable
    public String getBrownoutReason() {
        return get(CommonContextKeys.BROWNOUT_REASON);
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
    @Nullable
    public String getRouteVIP() {
        return get(KEY_VIP);
    }

    /**
     * sets routeVIP; that is the Eureka "vip" of registered instances
     */
    public void setRouteVIP(String sVip) {
        set(KEY_VIP, sVip);
    }

    public void setEndpoint(String endpoint) {
        set(KEY_ENDPOINT, endpoint);
    }

    @Nullable
    public String getEndpoint() {
        return get(KEY_ENDPOINT);
    }

    public void setEventProperty(String key, Object value) {
        getEventProperties().put(key, value);
    }

    public Map<String, Object> getEventProperties() {
        return eventProperties;
    }

    public List<FilterError> getFilterErrors() {
        return filterErrors;
    }

    public void setOriginReportedDuration(int duration) {
        set(KEY_ORIGIN_REPORTED_DURATION, duration);
    }

    public int getOriginReportedDuration() {
        Integer value = get(KEY_ORIGIN_REPORTED_DURATION);
        if (value != null) {
            return value;
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
