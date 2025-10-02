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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.http.HttpRequestInfo;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link StatsManager}.
 */
@ExtendWith(MockitoExtension.class)
class StatsManagerTest {

    @Test
    void testCollectRouteStats() {
        String route = "test";
        int status = 500;

        StatsManager sm = StatsManager.getManager();
        assertThat(sm).isNotNull();

        // 1st request
        sm.collectRouteStats(route, status);

        ConcurrentHashMap<Integer, RouteStatusCodeMonitor> routeStatusMap = sm.routeStatusMap.get("test");
        assertThat(routeStatusMap).isNotNull();

        // 2nd request
        sm.collectRouteStats(route, status);
    }

    @Test
    void testGetRouteStatusCodeMonitor() {
        StatsManager sm = StatsManager.getManager();
        assertThat(sm).isNotNull();
        sm.collectRouteStats("test", 500);
        assertThat(sm.getRouteStatusCodeMonitor("test", 500)).isNotNull();
    }

    @Test
    void testCollectRequestStats() {
        String host = "api.netflix.com";
        String proto = "https";

        HttpRequestInfo req = Mockito.mock(HttpRequestInfo.class);
        Headers headers = new Headers();
        when(req.getHeaders()).thenReturn(headers);
        headers.set(StatsManager.HOST_HEADER, host);
        headers.set(StatsManager.X_FORWARDED_PROTO_HEADER, proto);
        when(req.getClientIp()).thenReturn("127.0.0.1");

        StatsManager sm = StatsManager.getManager();
        sm.collectRequestStats(req);

        NamedCountingMonitor hostMonitor = sm.getHostMonitor(host);
        assertThat(hostMonitor).as("hostMonitor should not be null").isNotNull();

        NamedCountingMonitor protoMonitor = sm.getProtocolMonitor(proto);
        assertThat(protoMonitor).as("protoMonitor should not be null").isNotNull();

        assertThat(hostMonitor.getCount()).isEqualTo(1);
        assertThat(protoMonitor.getCount()).isEqualTo(1);
    }

    @Test
    void createsNormalizedHostKey() {
        assertThat(StatsManager.hostKey("ec2-174-129-179-89.compute-1.amazonaws.com"))
                .isEqualTo("host_EC2.amazonaws.com");
        assertThat(StatsManager.hostKey("12.345.6.789")).isEqualTo("host_IP");
        assertThat(StatsManager.hostKey("ip-10-86-83-168")).isEqualTo("host_IP");
        assertThat(StatsManager.hostKey("002.ie.llnw.nflxvideo.net")).isEqualTo("host_CDN.nflxvideo.net");
        assertThat(StatsManager.hostKey("netflix-635.vo.llnwd.net")).isEqualTo("host_CDN.llnwd.net");
        assertThat(StatsManager.hostKey("cdn-0.nflximg.com")).isEqualTo("host_CDN.nflximg.com");
    }

    @Test
    void extractsClientIpFromXForwardedFor() {
        String ip1 = "hi";
        String ip2 = "hey";
        assertThat(StatsManager.extractClientIpFromXForwardedFor(ip1)).isEqualTo(ip1);
        assertThat(StatsManager.extractClientIpFromXForwardedFor(String.format("%s,%s", ip1, ip2)))
                .isEqualTo(ip1);
        assertThat(StatsManager.extractClientIpFromXForwardedFor(String.format("%s, %s", ip1, ip2)))
                .isEqualTo(ip1);
    }

    @Test
    void isIPv6() {
        assertThat(StatsManager.isIPv6("0:0:0:0:0:0:0:1")).isTrue();
        assertThat(StatsManager.isIPv6("2607:fb10:2:232:72f3:95ff:fe03:a6e7")).isTrue();
        assertThat(StatsManager.isIPv6("127.0.0.1")).isFalse();
        assertThat(StatsManager.isIPv6("10.2.233.134")).isFalse();
    }
}
