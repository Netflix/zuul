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

package com.netflix.zuul.message.util;

import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.http.HttpQueryParams;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpRequestMessageImpl;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import java.util.Objects;

/**
 * Builder for a zuul http request. *exclusively* for use in unit tests.
 *
 * For default values initialized in the constructor:
 * <pre>
 * {@code new HttpRequestBuilder(context).withDefaults();}
 *</pre>
 *
 * For overrides :
 * <pre>
 * {@code new HttpRequestBuilder(context).withHeaders(httpHeaders).withQueryParams(requestParams).build();}
 * </pre>
 * @author Argha C
 * @since 5/11/21
 */
public final class HttpRequestBuilder {
    private SessionContext sessionContext;
    private String protocol;
    private String method;
    private String path;
    private HttpQueryParams queryParams;
    private Headers headers;
    private String clientIp;
    private String scheme;
    private int port;
    private String serverName;
    private boolean isBuilt;

    public HttpRequestBuilder(SessionContext context) {
        sessionContext = Objects.requireNonNull(context);
        protocol = HttpVersion.HTTP_1_1.text();
        method = "get";
        path = "/";
        queryParams = new HttpQueryParams();
        headers = new Headers();
        clientIp = "::1";
        scheme = "https";
        port = 443;
        isBuilt = false;
    }

    /**
     * Builds a request with basic defaults
     *
     * @return `HttpRequestMessage`
     */
    public HttpRequestMessage withDefaults() {
        return build();
    }

    public HttpRequestBuilder withHost(String hostName) {
        serverName = Objects.requireNonNull(hostName);
        return this;
    }

    public HttpRequestBuilder withHeaders(Headers requestHeaders) {
        headers = Objects.requireNonNull(requestHeaders);
        return this;
    }

    public HttpRequestBuilder withQueryParams(HttpQueryParams requestParams) {
        this.queryParams =  Objects.requireNonNull(requestParams);
        return this;
    }

    public HttpRequestBuilder withMethod(HttpMethod httpMethod) {
        method = Objects.requireNonNull(httpMethod).name();
        return this;
    }

    public HttpRequestBuilder withUri(String uri) {
        path = Objects.requireNonNull(uri);
        return this;
    }

    /**
     * Used to build a request with overriden values
     *
     * @return `HttpRequestMessage`
     */
    public HttpRequestMessage build() {
        if (isBuilt) {
            throw new IllegalStateException("Builder must only be invoked once!");
        }
        isBuilt = true;
        return new HttpRequestMessageImpl(sessionContext, protocol, method, path, queryParams, headers, clientIp, scheme, port,
                serverName);
    }
}
