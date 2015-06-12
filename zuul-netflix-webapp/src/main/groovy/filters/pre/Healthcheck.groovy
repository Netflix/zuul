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
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

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
    String responseBody() {
        RequestContext.getCurrentContext().getResponse().setContentType('application/xml')
        return "<health>ok</health>"
    }


    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit {

        @Mock
        HttpServletResponse response
        @Mock
        HttpServletRequest request

        @Test
        public void testHealthcheck() {
            RequestContext.setContextClass(RequestContext.class);
            Healthcheck hc = new Healthcheck();
            HttpServletRequest request = Mockito.mock(HttpServletRequest.class)
            HttpServletResponse response = Mockito.mock(HttpServletResponse.class)
            RequestContext.currentContext.request = request
            RequestContext.currentContext.response = response
            Mockito.when(request.getRequestURI()).thenReturn("/healthcheck")
            hc.run()

            Assert.assertTrue(hc.shouldFilter())

            Assert.assertTrue(RequestContext.currentContext.responseBody != null)
            Assert.assertTrue(RequestContext.currentContext.getResponseBody().contains("<health>ok</health>"))

            Mockito.when(request.getRequestURI()).thenReturn("healthcheck")
            Assert.assertTrue(hc.shouldFilter())

        }

    }

}
