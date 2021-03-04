/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.zuul.netty.timeouts;

import static com.netflix.client.config.CommonClientConfigKey.ReadTimeout;

import com.google.common.base.Strings;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.config.DynamicLongProperty;
import com.netflix.zuul.context.CommonContextKeys;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.origins.NettyOrigin;
import java.time.Duration;
import javax.annotation.Nullable;

/**
 * Origin Timeout Manager
 *
 * @author Arthur Gonigberg
 * @since February 24, 2021
 */
public class OriginTimeoutManager {

    private final NettyOrigin origin;

    public OriginTimeoutManager(NettyOrigin origin) {
        this.origin = origin;
    }

    private static final DynamicLongProperty MAX_OUTBOUND_READ_TIMEOUT_MS =
            new DynamicLongProperty("zuul.origin.readtimeout.max", Duration.ofSeconds(90).toMillis());

    public Object getRequestReadTimeout(IClientConfig requestConfig, Long defaultTimeout) {
        return requestConfig.getProperty(ReadTimeout, defaultTimeout);
    }

    public void setRequestReadTimeout(IClientConfig requestConfig, Object timeout) {
        requestConfig.setProperty(ReadTimeout, timeout);
    }

    public Object getOriginReadTimeout(Long noTimeout) {
        return origin.getClientConfig().getProperty(ReadTimeout, noTimeout);
    }

    public Object computeOverriddenReadTimeout(Object originalTimeout, IClientConfig requestConfig) {
        // check if there is a numeric override of the timeout
        Integer overriddenReadTimeout = requestConfig.get(ReadTimeout);
        if (overriddenReadTimeout != null) {
            return overriddenReadTimeout;
        }
        return originalTimeout;
    }

    public IClientConfig getRequestClientConfig(HttpRequestMessage zuulRequest) {
        IClientConfig overriddenClientConfig =
                (IClientConfig) zuulRequest.getContext().get(CommonContextKeys.REST_CLIENT_CONFIG);
        if (overriddenClientConfig == null) {
            overriddenClientConfig = new DefaultClientConfigImpl();
            zuulRequest.getContext().put(CommonContextKeys.REST_CLIENT_CONFIG, overriddenClientConfig);
        }

        return overriddenClientConfig;
    }

    public int computeReadTimeout(IClientConfig requestConfig, int attempt) {
        Duration readTimeout = getReadTimeout(requestConfig, attempt);
        return Math.toIntExact(readTimeout.toMillis());
    }

    /**
     * Derives the read timeout from the configuration.  This implementation prefers the longer of either the origin
     * timeout or the request timeout.
     *
     * @param requestConfig the config for the request.
     * @param attemptNum    the attempt number, starting at 1.
     */
    protected Duration getReadTimeout(IClientConfig requestConfig, int attemptNum) {
        Long noTimeout = null;
        // TODO(carl-mastrangelo): getProperty is deprecated, and suggests using the typed overload `get`.   However,
        //  the value is parsed using parseReadTimeoutMs, which supports String, implying not all timeouts are Integer.
        //  Figure out where a string ReadTimeout is coming from and replace it.
        Long originTimeout = parseReadTimeoutMs(getOriginReadTimeout(noTimeout));
        Long requestTimeout = parseReadTimeoutMs(getRequestReadTimeout(requestConfig, noTimeout));

        if (originTimeout == null && requestTimeout == null) {
            return Duration.ofMillis(MAX_OUTBOUND_READ_TIMEOUT_MS.get());
        } else if (originTimeout == null || requestTimeout == null) {
            return Duration.ofMillis(originTimeout == null ? requestTimeout : originTimeout);
        } else {
            // return the greater of two timeouts
            return Duration.ofMillis(originTimeout > requestTimeout ? originTimeout : requestTimeout);
        }
    }

    /**
     * An Integer is expected as an input, but supports parsing Long and String.  Returns {@code null} if no type is
     * acceptable.
     */
    @Nullable
    private Long parseReadTimeoutMs(Object p) {
        if (p instanceof String && !Strings.isNullOrEmpty((String) p)) {
            return Long.valueOf((String) p);
        } else if (p instanceof Long) {
            return (Long) p;
        } else if (p instanceof Integer) {
            return Long.valueOf((Integer) p);
        } else {
            return null;
        }
    }
}
