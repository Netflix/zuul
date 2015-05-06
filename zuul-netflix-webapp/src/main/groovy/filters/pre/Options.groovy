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
package pre

import com.netflix.zuul.context.HttpRequestMessage
import com.netflix.zuul.context.HttpResponseMessage
import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.filters.StaticResponseFilter
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.runners.MockitoJUnitRunner

import static org.junit.Assert.assertTrue
import static org.mockito.Mockito.when

/**
 * Created by IntelliJ IDEA.
 * User: mcohen
 * Date: 2/1/12
 * Time: 7:56 AM
 */
class Options extends StaticResponseFilter {

    @Override
    boolean shouldFilter(SessionContext ctx) {
        HttpRequestMessage request = ctx.getRequest()
        String method = request.getMethod();
        if (method.equalsIgnoreCase("options")) return true;
    }


    @Override
    String uri() {
        return "any path here"
    }

    @Override
    String responseBody(SessionContext ctx) {
        return "" // empty response
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit {

        Options filter
        SessionContext ctx
        HttpResponseMessage response
        Throwable th

        @Mock
        HttpRequestMessage request

        @Before
        public void setup() {
            filter = new Options()
            response = new HttpResponseMessage(99)
            ctx = new SessionContext(request, response)
        }

        @Test
        public void testClientAccessPolicy() {

            when(request.getPath()).thenReturn("/anything")
            when(request.getMethod()).thenReturn("OPTIONS")

            filter.apply(ctx)

            assertTrue(filter.shouldFilter(ctx))

            assertTrue(response.getBody() != null)
            String bodyStr = new String(response.getBody(), "UTF-8")
            assertTrue(bodyStr.isEmpty())
        }

    }

}
