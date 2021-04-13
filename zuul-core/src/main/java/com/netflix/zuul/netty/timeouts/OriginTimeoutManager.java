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

import com.google.common.annotations.VisibleForTesting;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.config.DynamicLongProperty;
import com.netflix.zuul.context.CommonContextKeys;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.origins.NettyOrigin;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
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
        this.origin = Objects.requireNonNull(origin);
    }

    @VisibleForTesting
    static final DynamicLongProperty MAX_OUTBOUND_READ_TIMEOUT_MS =
            new DynamicLongProperty("zuul.origin.readtimeout.max", Duration.ofSeconds(90).toMillis());

    /**
     * Derives the read timeout from the configuration.  This implementation prefers the longer of either the origin
     * timeout or the request timeout.
     * <p>
     * This method can also be used to validate timeout and deadline boundaries and throw exceptions as needed. If
     * extending this method to do validation, you should extend {@link com.netflix.zuul.exception.OutboundException}
     * and set the appropriate {@link com.netflix.zuul.exception.ErrorType}.
     *
     * @param request    the request.
     * @param attemptNum the attempt number, starting at 1.
     */
    public Duration computeReadTimeout(HttpRequestMessage request, int attemptNum) {
        IClientConfig clientConfig = getRequestClientConfig(request);
        Long originTimeout = getOriginReadTimeout();
        Long requestTimeout = getRequestReadTimeout(clientConfig);

        long computedTimeout;
        if (originTimeout == null && requestTimeout == null) {
            computedTimeout = MAX_OUTBOUND_READ_TIMEOUT_MS.get();
        } else if (originTimeout == null || requestTimeout == null) {
            computedTimeout = originTimeout == null ? requestTimeout : originTimeout;
        } else {
            // return the stricter (i.e. lower) of the two timeouts
            computedTimeout = Math.min(originTimeout, requestTimeout);
        }

        // enforce max timeout upperbound
        return Duration.ofMillis(Math.min(computedTimeout, MAX_OUTBOUND_READ_TIMEOUT_MS.get()));
    }

    /**
     * This method will create a new client config or retrieve the existing one from the current request.
     *
     * @param zuulRequest - the request
     * @return the config
     */
    protected IClientConfig getRequestClientConfig(HttpRequestMessage zuulRequest) {
        IClientConfig overriddenClientConfig =
                (IClientConfig) zuulRequest.getContext().get(CommonContextKeys.REST_CLIENT_CONFIG);
        if (overriddenClientConfig == null) {
            overriddenClientConfig = new DefaultClientConfigImpl();
            zuulRequest.getContext().put(CommonContextKeys.REST_CLIENT_CONFIG, overriddenClientConfig);
        }

        return overriddenClientConfig;
    }

    /**
     * This method makes the assumption that the timeout is a numeric value
     */
    @Nullable
    private Long getRequestReadTimeout(IClientConfig clientConfig) {
        return Optional.ofNullable(clientConfig.get(CommonClientConfigKey.ReadTimeout))
                .map(Long::valueOf)
                .orElse(null);
    }

    /**
     * This method makes the assumption that the timeout is a numeric value
     */
    @Nullable
    private Long getOriginReadTimeout() {
        return Optional.ofNullable(origin.getClientConfig().get(CommonClientConfigKey.ReadTimeout))
                .map(Long::valueOf)
                .orElse(null);
    }
}
