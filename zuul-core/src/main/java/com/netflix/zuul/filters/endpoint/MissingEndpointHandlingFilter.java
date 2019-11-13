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

package com.netflix.zuul.filters.endpoint;

import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.exception.ZuulException;
import com.netflix.zuul.filters.SyncZuulFilterAdapter;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by saroskar on 2/13/17.
 */
public final class MissingEndpointHandlingFilter extends SyncZuulFilterAdapter<HttpRequestMessage, HttpResponseMessage> {
    private final String name;

    private static final Logger LOG = LoggerFactory.getLogger(MissingEndpointHandlingFilter.class);

    public MissingEndpointHandlingFilter(String name) {
        this.name = name;
    }

    @Override
    public HttpResponseMessage apply(HttpRequestMessage request) {
        final SessionContext zuulCtx = request.getContext();
        zuulCtx.setErrorResponseSent(true);
        final String errMesg = "Missing Endpoint filter, name = " + name;
        zuulCtx.setError(new ZuulException(errMesg, true));
        LOG.error(errMesg);
        return new HttpResponseMessageImpl(zuulCtx, request, 500);
    }

    @Override
    public String filterName() {
        return name;
    }

    @Override
    public HttpResponseMessage getDefaultOutput(final HttpRequestMessage input) {
        return HttpResponseMessageImpl.defaultErrorResponse(input);
    }

}
