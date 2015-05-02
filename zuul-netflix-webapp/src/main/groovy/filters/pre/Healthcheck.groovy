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
package filters.pre

import com.netflix.zuul.context.HttpRequestMessage
import com.netflix.zuul.context.HttpResponseMessage
import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.filters.StaticResponseFilter
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner

/**
 * @author Mikey Cohen
 * Date: 2/1/12
 * Time: 7:56 AM
 */
class Healthcheck extends StaticResponseFilter {

    @Override
    String filterType() {
        return "healthcheck"
    }

    @Override
    String uri() {
        return "/healthcheck"
    }

    @Override
    String responseBody(SessionContext ctx) {
        HttpResponseMessage response = ctx.getResponse()
        response.headers.set('Content-Type', 'application/xml')
        return "<health>ok</health>"
    }


    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit
    {
        Healthcheck filter
        SessionContext ctx
        HttpResponseMessage response
        Throwable th

        @Mock
        HttpRequestMessage request

        @Before
        public void setup() {
            filter = new Healthcheck()
            response = new HttpResponseMessage(99)
            ctx = new SessionContext(request, response)
        }

        @Test
        public void testHealthcheck() {

            Mockito.when(request.getPath()).thenReturn("/healthcheck")

            filter.apply(ctx)

            Assert.assertTrue(filter.shouldFilter(ctx))

            Assert.assertTrue(response.getBody() != null)
            String bodyStr = new String(response.getBody(), "UTF-8")
            Assert.assertTrue(bodyStr.contains("<health>ok</health>"))

            Mockito.when(request.getPath()).thenReturn("healthcheck")
            Assert.assertTrue(filter.shouldFilter(ctx))

        }

    }

}
