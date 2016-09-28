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
package outbound

import com.netflix.zuul.context.Debug
import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.filters.BaseFilterTest
import com.netflix.zuul.filters.FilterType
import com.netflix.zuul.filters.http.HttpOutboundSyncFilter
import com.netflix.zuul.message.http.HttpResponseMessage
import com.netflix.zuul.stats.StatsManager
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.runners.MockitoJUnitRunner

import static org.mockito.Mockito.when

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
        sm.collectRequestStats(response.getInboundRequest());
        sm.collectRouteStats(ctx.route, status);
        dumpRoutingDebug(ctx)
        dumpRequestDebug(ctx)
    }

    public void dumpRequestDebug(SessionContext context)
    {
        String uuid = context.getUUID()
        List<String> rd = Debug.getRequestDebug(context)
        rd?.each {
            println("${uuid} ${it}");
        }
    }

    public void dumpRoutingDebug(SessionContext ctx)
    {
        String uuid = ctx.getUUID()
        List<String> rd = Debug.getRoutingDebug(ctx);
        rd?.each {
            println("${uuid} ${it}");
        }
    }


    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit extends BaseFilterTest {

        Stats filter

        @Before
        public void setup() {
            super.setup()
            filter = new Stats()
            when(response.getStatus()).thenReturn(99)
        }

        @Test
        public void testHeaderResponse() {

            Assert.assertTrue(filter.filterType().equals(FilterType.OUTBOUND))

            context.route = "testStats"
            filter.apply(response)

            Assert.assertTrue(StatsManager.manager.getRouteStatusCodeMonitor("testStats", 99) != null)
        }

    }

}
