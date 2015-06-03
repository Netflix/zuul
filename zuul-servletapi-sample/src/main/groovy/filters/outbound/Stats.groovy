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
package filters.outbound

import com.netflix.zuul.context.Headers
import com.netflix.zuul.context.HttpRequestMessage
import com.netflix.zuul.context.HttpResponseMessage
import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.filters.http.HttpOutboundSyncFilter
import com.netflix.zuul.stats.StatsManager
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner

/**
 * @author Mikey Cohen
 * Date: 2/3/12
 * Time: 2:48 PM
 */
class Stats extends HttpOutboundSyncFilter
{
    @Override
    int filterOrder() {
        return 2000
    }

    @Override
    boolean shouldFilter(HttpResponseMessage response) {
        return true
    }

    @Override
    HttpResponseMessage apply(HttpResponseMessage response)
    {
        SessionContext ctx = response.getContext()
        int status = response.getStatus()
        StatsManager sm = StatsManager.manager
        sm.collectRequestStats(response.getRequest());
        sm.collectRouteStats(ctx.getAttributes().route, status);
        dumpRoutingDebug(ctx)
        dumpRequestDebug(ctx)
    }

    public void dumpRequestDebug(SessionContext ctx) {
        List<String> rd = (List<String>) ctx.getAttributes().get("requestDebug");
        rd?.each {
            println("REQUEST_DEBUG::${it}");
        }
    }

    public void dumpRoutingDebug(SessionContext ctx) {
        List<String> rd = (List<String>) ctx.getAttributes().get("routingDebug");
        rd?.each {
            println("ZUUL_DEBUG::${it}");
        }
    }


    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit {

        Stats filter
        SessionContext ctx
        HttpResponseMessage response
        Headers reqHeaders

        @Mock
        HttpRequestMessage request

        @Before
        public void setup() {
            filter = new Stats()
            ctx = new SessionContext()
            Mockito.when(request.getContext()).thenReturn(ctx)
            response = new HttpResponseMessage(ctx, request, 99)

            reqHeaders = new Headers()
            Mockito.when(request.getHeaders()).thenReturn(reqHeaders)
        }

        @Test
        public void testHeaderResponse() {

            Assert.assertTrue(filter.filterType().equals("out"))

            ctx.getAttributes().route = "testStats"
            filter.apply(response)

            Assert.assertTrue(StatsManager.manager.getRouteStatusCodeMonitor("testStats", 99) != null)
        }

    }

}
