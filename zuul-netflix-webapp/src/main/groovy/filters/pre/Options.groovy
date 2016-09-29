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

import com.netflix.zuul.context.RequestContext
import com.netflix.zuul.filters.StaticResponseFilter
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.runners.MockitoJUnitRunner

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static org.junit.Assert.assertTrue
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when

/**
 * Created by IntelliJ IDEA.
 * User: mcohen
 * Date: 2/1/12
 * Time: 7:56 AM
 */
class Options extends StaticResponseFilter {

    boolean shouldFilter() {
        String method = RequestContext.currentContext.getRequest() getMethod();
        if (method.equalsIgnoreCase("options")) return true;
    }


    @Override
    String uri() {
        return "any path here"
    }

    @Override
    String responseBody() {
        return "" // empty response
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit {

        @Mock
        HttpServletResponse response
        @Mock
        HttpServletRequest request

        @Test
        public void testClientAccessPolicy() {

            RequestContext.setContextClass(RequestContext.class);

            Options options = new Options()

            HttpServletRequest request = mock(HttpServletRequest.class)
            RequestContext.currentContext.request = request
            when(request.getRequestURI()).thenReturn("/anything")
            when(request.getMethod()).thenReturn("OPTIONS")
            options.run()

            assertTrue(options.shouldFilter())

            assertTrue(RequestContext.currentContext.responseBody != null)
            assertTrue(RequestContext.currentContext.getResponseBody().isEmpty())
        }

    }

}
