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

package com.netflix.zuul.netty.server;

import static com.netflix.zuul.netty.server.SocketAddressProperty.BindType.ANY;
import static com.netflix.zuul.netty.server.SocketAddressProperty.BindType.ANY_LOCAL;
import static com.netflix.zuul.netty.server.SocketAddressProperty.BindType.IPV4_ANY;
import static com.netflix.zuul.netty.server.SocketAddressProperty.BindType.IPV4_LOCAL;
import static com.netflix.zuul.netty.server.SocketAddressProperty.BindType.IPV6_ANY;
import static com.netflix.zuul.netty.server.SocketAddressProperty.BindType.IPV6_LOCAL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.netflix.zuul.netty.server.SocketAddressProperty.BindType;
import io.netty.channel.unix.DomainSocketAddress;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SocketAddressPropertyTest {

    @Test
    public void defaultValueWorks() {
        SocketAddressProperty prop = new SocketAddressProperty("com.netflix.zuul.netty.server.testprop", "=7001");

        SocketAddress address = prop.getValue();
        assertEquals(InetSocketAddress.class, address.getClass());
        InetSocketAddress inetSocketAddress = (InetSocketAddress) address;
        assertEquals(7001, inetSocketAddress.getPort());
        assertFalse(inetSocketAddress.isUnresolved());
    }

    @Test
    public void bindTypeWorks_any() {
        SocketAddress address = SocketAddressProperty.Decoder.INSTANCE.apply("ANY=7001");

        assertEquals(InetSocketAddress.class, address.getClass());
        InetSocketAddress inetSocketAddress = (InetSocketAddress) address;
        assertEquals(7001, inetSocketAddress.getPort());
        assertFalse(inetSocketAddress.isUnresolved());
    }

    @Test
    public void bindTypeWorks_blank() {
        SocketAddress address = SocketAddressProperty.Decoder.INSTANCE.apply("=7001");

        assertEquals(InetSocketAddress.class, address.getClass());
        InetSocketAddress inetSocketAddress = (InetSocketAddress) address;
        assertEquals(7001, inetSocketAddress.getPort());
        assertFalse(inetSocketAddress.isUnresolved());
    }

    @Test
    public void bindTypeWorks_ipv4Any() {
        SocketAddress address = SocketAddressProperty.Decoder.INSTANCE.apply("IPV4_ANY=7001");

        assertEquals(InetSocketAddress.class, address.getClass());
        InetSocketAddress inetSocketAddress = (InetSocketAddress) address;
        assertEquals(7001, inetSocketAddress.getPort());
        assertFalse(inetSocketAddress.isUnresolved());
        assertTrue(inetSocketAddress.getAddress() instanceof Inet4Address);
        assertTrue(inetSocketAddress.getAddress().isAnyLocalAddress());
    }

    @Test
    public void bindTypeWorks_ipv6Any() {
        SocketAddress address = SocketAddressProperty.Decoder.INSTANCE.apply("IPV6_ANY=7001");

        assertEquals(InetSocketAddress.class, address.getClass());
        InetSocketAddress inetSocketAddress = (InetSocketAddress) address;
        assertEquals(7001, inetSocketAddress.getPort());
        assertFalse(inetSocketAddress.isUnresolved());
        assertTrue(inetSocketAddress.getAddress() instanceof Inet6Address);
        assertTrue(inetSocketAddress.getAddress().isAnyLocalAddress());
    }

    @Test
    public void bindTypeWorks_anyLocal() {
        SocketAddress address = SocketAddressProperty.Decoder.INSTANCE.apply("ANY_LOCAL=7001");

        assertEquals(InetSocketAddress.class, address.getClass());
        InetSocketAddress inetSocketAddress = (InetSocketAddress) address;
        assertEquals(7001, inetSocketAddress.getPort());
        assertFalse(inetSocketAddress.isUnresolved());
        assertTrue(inetSocketAddress.getAddress().isLoopbackAddress());
    }

    @Test
    public void bindTypeWorks_ipv4Local() {
        SocketAddress address = SocketAddressProperty.Decoder.INSTANCE.apply("IPV4_LOCAL=7001");

        assertEquals(InetSocketAddress.class, address.getClass());
        InetSocketAddress inetSocketAddress = (InetSocketAddress) address;
        assertEquals(7001, inetSocketAddress.getPort());
        assertFalse(inetSocketAddress.isUnresolved());
        assertTrue(inetSocketAddress.getAddress() instanceof Inet4Address);
        assertTrue(inetSocketAddress.getAddress().isLoopbackAddress());
    }

    @Test
    public void bindTypeWorks_ipv6Local() {
        SocketAddress address = SocketAddressProperty.Decoder.INSTANCE.apply("IPV6_LOCAL=7001");

        assertEquals(InetSocketAddress.class, address.getClass());
        InetSocketAddress inetSocketAddress = (InetSocketAddress) address;
        assertEquals(7001, inetSocketAddress.getPort());
        assertFalse(inetSocketAddress.isUnresolved());
        assertTrue(inetSocketAddress.getAddress() instanceof Inet6Address);
        assertTrue(inetSocketAddress.getAddress().isLoopbackAddress());
    }

    @Test
    public void bindTypeWorks_uds() {
        SocketAddress address = SocketAddressProperty.Decoder.INSTANCE.apply("UDS=/var/run/zuul.sock");

        assertEquals(DomainSocketAddress.class, address.getClass());
        DomainSocketAddress domainSocketAddress = (DomainSocketAddress) address;
        assertEquals("/var/run/zuul.sock", domainSocketAddress.path());
    }

    @Test
    public void bindTypeWorks_udsWithEquals() {
        SocketAddress address = SocketAddressProperty.Decoder.INSTANCE.apply("UDS=/var/run/zuul=.sock");

        assertEquals(DomainSocketAddress.class, address.getClass());
        DomainSocketAddress domainSocketAddress = (DomainSocketAddress) address;
        assertEquals("/var/run/zuul=.sock", domainSocketAddress.path());
    }

    @Test
    public void failsOnMissingEqual() {
        assertThrows(IllegalArgumentException.class, () -> {
            SocketAddressProperty.Decoder.INSTANCE.apply("ANY");
        });
    }

    @Test
    public void failsOnBadPort() {
        for (BindType type : Arrays.asList(ANY, IPV4_ANY, IPV6_ANY, ANY_LOCAL, IPV4_LOCAL, IPV6_LOCAL)) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                SocketAddressProperty.Decoder.INSTANCE.apply(type.name() + "=bogus");
            });
            assertTrue(exception.getMessage().contains("Port"));
        }
    }
}
