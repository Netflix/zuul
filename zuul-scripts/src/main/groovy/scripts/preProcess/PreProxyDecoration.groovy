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
package scripts.preProcess


import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import com.netflix.zuul.groovy.ZuulFilter
import com.netflix.zuul.context.RequestContext
import com.netflix.zuul.exception.ZuulException

/**
 * @author Mikey Cohen
 * Date: 1/5/12
 * Time: 1:03 PM
 */
public class PreProxyDecoration extends ZuulFilter {

    @Override
    String filterType() {
        return "pre"
    }

    @Override
    int filterOrder() {
        return 20
    }

    @Override
    boolean shouldFilter() {
        return true
    }

    @Override
    Object run() {
        if (RequestContext.currentContext.getRequest().getParameter("url") != null) {
            try {
                RequestContext.getCurrentContext().routeHost = new URL(RequestContext.currentContext.getRequest().getParameter("url"))
                RequestContext.currentContext.setResponseGZipped(true)
            } catch (MalformedURLException e) {
                throw new ZuulException(e, "Malformed URL", 400, "MALFORMED_URL")
            }
        }
        setProxyHeaders()
        return null
    }

    void setProxyHeaders() {
        RequestContext context = RequestContext.currentContext
        context.addZuulRequestHeader("X-Netflix.request.toplevel.uuid", UUID.randomUUID().toString())
        context.addZuulRequestHeader("X-Forwarded-For", context.getRequest().remoteAddr)
        context.addZuulRequestHeader("X-Netflix.client-host", context.getRequest().getHeader("Host"))
        if (context.getRequest().getHeader("X-Forwarded-Proto") != null) {
            context.addZuulRequestHeader("X-Netflix.client-proto", context.getRequest().getHeader("X-Forwarded-Proto"))
        }
//        context.addZuulRequestHeader("X-Netflix-User-Id", getUserID) //todo double check this requirement


    }


    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit {

        @Mock
        HttpServletResponse response
        @Mock
        HttpServletRequest request





        @Test
        public void testPreProxyHeaders() {

            PreProxyDecoration ppd = new PreProxyDecoration()
            HttpServletRequest request = Mockito.mock(HttpServletRequest.class)
            RequestContext.currentContext.request = request
            Mockito.when(request.remoteAddr).thenReturn("1.1.1.1")
            Mockito.when(request.getHeader("Host")).thenReturn("moldfarm.com")
            Mockito.when(request.getHeader("X-Forwarded-Proto")).thenReturn("https")

            ppd.setProxyHeaders()

            Map<String, String> headers = RequestContext.currentContext.zuulRequestHeaders
            Assert.assertNotNull(headers["x-netflix.request.toplevel.uuid"])
            Assert.assertNotNull(headers["x-forwarded-for"])
            Assert.assertNotNull(headers["x-netflix.client-host"])
            Assert.assertNotNull(headers["x-netflix.client-proto"])

        }

    }

}
