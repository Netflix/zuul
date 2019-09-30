/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.zuul.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.http.HttpRequestInfo;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * Unit tests for {@link StatsManager}.
 */
@RunWith(MockitoJUnitRunner.class)
public class StatsManagerTest {

    @Test
    public void testCollectRouteStats() {
        String route = "test";
        int status = 500;

        StatsManager sm = StatsManager.getManager();
        assertNotNull(sm);

        // 1st request
        sm.collectRouteStats(route, status);

        ConcurrentHashMap<Integer, RouteStatusCodeMonitor> routeStatusMap = sm.routeStatusMap.get("test");
        assertNotNull(routeStatusMap);


        RouteStatusCodeMonitor routeStatusMonitor = routeStatusMap.get(status);


        // 2nd request
        sm.collectRouteStats(route, status);

    }

    @Test
    public void testGetRouteStatusCodeMonitor() {
        StatsManager sm = StatsManager.getManager();
        assertNotNull(sm);
        sm.collectRouteStats("test", 500);
        assertNotNull(sm.getRouteStatusCodeMonitor("test", 500));
    }

    @Test
    public void testCollectRequestStats() {
        final String host = "api.netflix.com";
        final String proto = "https";

        final HttpRequestInfo req = Mockito.mock(HttpRequestInfo.class);
        Headers headers = new Headers();
        when(req.getHeaders()).thenReturn(headers);
        headers.set(StatsManager.HOST_HEADER, host);
        headers.set(StatsManager.X_FORWARDED_PROTO_HEADER, proto);
        when(req.getClientIp()).thenReturn("127.0.0.1");

        final StatsManager sm = StatsManager.getManager();
        sm.collectRequestStats(req);

        final NamedCountingMonitor hostMonitor = sm.getHostMonitor(host);
        assertNotNull("hostMonitor should not be null", hostMonitor);

        final NamedCountingMonitor protoMonitor = sm.getProtocolMonitor(proto);
        assertNotNull("protoMonitor should not be null", protoMonitor);

        assertEquals(1, hostMonitor.getCount());
        assertEquals(1, protoMonitor.getCount());
    }

    @Test
    public void createsNormalizedHostKey() {
        assertEquals("host_EC2.amazonaws.com", StatsManager.hostKey("ec2-174-129-179-89.compute-1.amazonaws.com"));
        assertEquals("host_IP", StatsManager.hostKey("12.345.6.789"));
        assertEquals("host_IP", StatsManager.hostKey("ip-10-86-83-168"));
        assertEquals("host_CDN.nflxvideo.net", StatsManager.hostKey("002.ie.llnw.nflxvideo.net"));
        assertEquals("host_CDN.llnwd.net", StatsManager.hostKey("netflix-635.vo.llnwd.net"));
        assertEquals("host_CDN.nflximg.com", StatsManager.hostKey("cdn-0.nflximg.com"));
    }

    @Test
    public void extractsClientIpFromXForwardedFor() {
        final String ip1 = "hi";
        final String ip2 = "hey";
        assertEquals(ip1, StatsManager.extractClientIpFromXForwardedFor(ip1));
        assertEquals(ip1, StatsManager.extractClientIpFromXForwardedFor(String.format("%s,%s", ip1, ip2)));
        assertEquals(ip1, StatsManager.extractClientIpFromXForwardedFor(String.format("%s, %s", ip1, ip2)));
    }

    @Test
    public void isIPv6() {
        assertTrue(StatsManager.isIPv6("0:0:0:0:0:0:0:1"));
        assertTrue(StatsManager.isIPv6("2607:fb10:2:232:72f3:95ff:fe03:a6e7"));
        assertFalse(StatsManager.isIPv6("127.0.0.1"));
        assertFalse(StatsManager.isIPv6("10.2.233.134"));
    }
}
