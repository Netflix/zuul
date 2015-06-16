/*
 * Copyright 2013 Netflix, Inc.
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
package filters.endpoint

import com.netflix.zuul.context.HttpQueryParams
import com.netflix.zuul.context.HttpRequestMessage
import com.netflix.zuul.context.HttpResponseMessage
import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.exception.ZuulException
import com.netflix.zuul.filters.http.HttpSyncEndpoint
import com.netflix.zuul.monitoring.MonitoringHelper
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner

import static org.junit.Assert.assertEquals

class ErrorResponse extends HttpSyncEndpoint
{
    @Override
    HttpResponseMessage apply(HttpRequestMessage request)
    {
        SessionContext context = request.getContext()

        HttpResponseMessage response = new HttpResponseMessage(context, request, 500)

        Throwable e = context.getThrowable()

        if (Class.isAssignableFrom(ZuulException.class)) {
            String cause = ((ZuulException) e).errorCause
            if (cause == null) cause = "UNKNOWN"
            response.getHeaders().add("X-Netflix-Error-Cause", "Zuul Error: " + cause)
        }
        else {
            response.getHeaders().add("X-Zuul-Error-Cause", "Zuul Error UNKNOWN Cause")
        }

        String errorMessage = e.getMessage() == null ? "Unknown Error" : e.getMessage()
        response.setBody(errorMessage.getBytes("UTF-8"))

        return response
    }


    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit
    {
        ErrorResponse filter
        SessionContext ctx
        Throwable th

        @Mock
        HttpRequestMessage request

        HttpQueryParams queryParams

        @Before
        public void setup() {
            MonitoringHelper.initMocks()
            filter = new ErrorResponse()
            ctx = new SessionContext()
            Mockito.when(request.getContext()).thenReturn(ctx)

            queryParams = new HttpQueryParams()
            Mockito.when(request.getQueryParams()).thenReturn(queryParams)
        }

        @Test
        public void testApply()
        {
            th = new ZuulException("test", "a-cause")
            ctx.throwable = th

            HttpResponseMessage response = filter.apply(request)
            assertEquals(500, response.getStatus())
            assertEquals("test", new String(response.getBody(), "UTF-8"))
        }
    }
}