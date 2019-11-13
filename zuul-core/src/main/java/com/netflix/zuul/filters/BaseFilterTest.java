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

package com.netflix.zuul.filters;

import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.http.*;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.when;

/**
 * A convenience superclass to extend when writing Filter unit-tests.
 *
 * @author Mike Smith
 */
@RunWith(MockitoJUnitRunner.class)
public abstract class BaseFilterTest
{
    @Mock
    protected HttpResponseMessage response;
    @Mock
    protected HttpRequestMessage request;
    @Mock
    protected HttpRequestInfo originalRequest;
    @Mock
    protected HttpResponseInfo originResponse;

    protected SessionContext context;
    protected Headers originalRequestHeaders;
    protected Headers requestHeaders;
    protected HttpQueryParams requestParams;
    protected Cookies requestCookies;
    protected Headers originResponseHeaders;
    protected Headers responseHeaders;

    @Before
    public void setup()
    {
        context = new SessionContext();

        when(request.getContext()).thenReturn(context);
        when(response.getContext()).thenReturn(context);
        when(request.getInboundRequest()).thenReturn(originalRequest);
        when(response.getOutboundRequest()).thenReturn(request);
        when(response.getInboundRequest()).thenReturn(originalRequest);
        when(response.getInboundResponse()).thenReturn(originResponse);

        originResponseHeaders = new Headers();
        when(originResponse.getHeaders()).thenReturn(originResponseHeaders);

        originalRequestHeaders = new Headers();
        requestHeaders = new Headers();
        when(request.getHeaders()).thenReturn(requestHeaders);
        when(originalRequest.getHeaders()).thenReturn(originalRequestHeaders);

        requestParams = new HttpQueryParams();
        when(request.getQueryParams()).thenReturn(requestParams);
        when(originalRequest.getQueryParams()).thenReturn(requestParams);

        requestCookies = new Cookies();
        when(request.parseCookies()).thenReturn(requestCookies);

        responseHeaders = new Headers();
        when(response.getHeaders()).thenReturn(responseHeaders);
    }

    protected void setRequestHost(String host) {
        when(originalRequest.getOriginalHost()).thenReturn(host);
    }
}
