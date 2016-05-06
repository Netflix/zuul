package com.netflix.zuul.stats;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;

import static com.netflix.zuul.stats.StatsManager.HOST_HEADER;
import static com.netflix.zuul.stats.StatsManager.X_FORWARDED_PROTO_HEADER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.powermock.api.mockito.PowerMockito.when;


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

        RouteStatusCodeMonitor routeStatusMonitor = sm.getRouteStatusCodeMonitor("test", status);
        assertNotNull(routeStatusMonitor);

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

        final HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        when(req.getHeader(HOST_HEADER)).thenReturn(host);
        when(req.getHeader(X_FORWARDED_PROTO_HEADER)).thenReturn(proto);
        when(req.getRemoteAddr()).thenReturn("127.0.0.1");

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