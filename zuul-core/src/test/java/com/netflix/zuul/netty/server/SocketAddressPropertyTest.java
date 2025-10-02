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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.netflix.zuul.netty.server.SocketAddressProperty.BindType;
import io.netty.channel.unix.DomainSocketAddress;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SocketAddressPropertyTest {

    @Test
    void defaultValueWorks() {
        SocketAddressProperty prop = new SocketAddressProperty("com.netflix.zuul.netty.server.testprop", "=7001");

        SocketAddress address = prop.getValue();
        assertThat(address.getClass()).isEqualTo(InetSocketAddress.class);
        InetSocketAddress inetSocketAddress = (InetSocketAddress) address;
        assertThat(inetSocketAddress.getPort()).isEqualTo(7001);
        assertThat(inetSocketAddress.isUnresolved()).isFalse();
    }

    @Test
    void bindTypeWorks_any() {
        SocketAddress address = SocketAddressProperty.Decoder.INSTANCE.apply("ANY=7001");

        assertThat(address.getClass()).isEqualTo(InetSocketAddress.class);
        InetSocketAddress inetSocketAddress = (InetSocketAddress) address;
        assertThat(inetSocketAddress.getPort()).isEqualTo(7001);
        assertThat(inetSocketAddress.isUnresolved()).isFalse();
    }

    @Test
    void bindTypeWorks_blank() {
        SocketAddress address = SocketAddressProperty.Decoder.INSTANCE.apply("=7001");

        assertThat(address.getClass()).isEqualTo(InetSocketAddress.class);
        InetSocketAddress inetSocketAddress = (InetSocketAddress) address;
        assertThat(inetSocketAddress.getPort()).isEqualTo(7001);
        assertThat(inetSocketAddress.isUnresolved()).isFalse();
    }

    @Test
    void bindTypeWorks_ipv4Any() {
        SocketAddress address = SocketAddressProperty.Decoder.INSTANCE.apply("IPV4_ANY=7001");

        assertThat(address.getClass()).isEqualTo(InetSocketAddress.class);
        InetSocketAddress inetSocketAddress = (InetSocketAddress) address;
        assertThat(inetSocketAddress.getPort()).isEqualTo(7001);
        assertThat(inetSocketAddress.isUnresolved()).isFalse();
        assertThat(inetSocketAddress.getAddress() instanceof Inet4Address).isTrue();
        assertThat(inetSocketAddress.getAddress().isAnyLocalAddress()).isTrue();
    }

    @Test
    void bindTypeWorks_ipv6Any() {
        SocketAddress address = SocketAddressProperty.Decoder.INSTANCE.apply("IPV6_ANY=7001");

        assertThat(address.getClass()).isEqualTo(InetSocketAddress.class);
        InetSocketAddress inetSocketAddress = (InetSocketAddress) address;
        assertThat(inetSocketAddress.getPort()).isEqualTo(7001);
        assertThat(inetSocketAddress.isUnresolved()).isFalse();
        assertThat(inetSocketAddress.getAddress() instanceof Inet6Address).isTrue();
        assertThat(inetSocketAddress.getAddress().isAnyLocalAddress()).isTrue();
    }

    @Test
    void bindTypeWorks_anyLocal() {
        SocketAddress address = SocketAddressProperty.Decoder.INSTANCE.apply("ANY_LOCAL=7001");

        assertThat(address.getClass()).isEqualTo(InetSocketAddress.class);
        InetSocketAddress inetSocketAddress = (InetSocketAddress) address;
        assertThat(inetSocketAddress.getPort()).isEqualTo(7001);
        assertThat(inetSocketAddress.isUnresolved()).isFalse();
        assertThat(inetSocketAddress.getAddress().isLoopbackAddress()).isTrue();
    }

    @Test
    void bindTypeWorks_ipv4Local() {
        SocketAddress address = SocketAddressProperty.Decoder.INSTANCE.apply("IPV4_LOCAL=7001");

        assertThat(address.getClass()).isEqualTo(InetSocketAddress.class);
        InetSocketAddress inetSocketAddress = (InetSocketAddress) address;
        assertThat(inetSocketAddress.getPort()).isEqualTo(7001);
        assertThat(inetSocketAddress.isUnresolved()).isFalse();
        assertThat(inetSocketAddress.getAddress() instanceof Inet4Address).isTrue();
        assertThat(inetSocketAddress.getAddress().isLoopbackAddress()).isTrue();
    }

    @Test
    void bindTypeWorks_ipv6Local() {
        SocketAddress address = SocketAddressProperty.Decoder.INSTANCE.apply("IPV6_LOCAL=7001");

        assertThat(address.getClass()).isEqualTo(InetSocketAddress.class);
        InetSocketAddress inetSocketAddress = (InetSocketAddress) address;
        assertThat(inetSocketAddress.getPort()).isEqualTo(7001);
        assertThat(inetSocketAddress.isUnresolved()).isFalse();
        assertThat(inetSocketAddress.getAddress() instanceof Inet6Address).isTrue();
        assertThat(inetSocketAddress.getAddress().isLoopbackAddress()).isTrue();
    }

    @Test
    void bindTypeWorks_uds() {
        SocketAddress address = SocketAddressProperty.Decoder.INSTANCE.apply("UDS=/var/run/zuul.sock");

        assertThat(address.getClass()).isEqualTo(DomainSocketAddress.class);
        DomainSocketAddress domainSocketAddress = (DomainSocketAddress) address;
        assertThat(domainSocketAddress.path()).isEqualTo("/var/run/zuul.sock");
    }

    @Test
    void bindTypeWorks_udsWithEquals() {
        SocketAddress address = SocketAddressProperty.Decoder.INSTANCE.apply("UDS=/var/run/zuul=.sock");

        assertThat(address.getClass()).isEqualTo(DomainSocketAddress.class);
        DomainSocketAddress domainSocketAddress = (DomainSocketAddress) address;
        assertThat(domainSocketAddress.path()).isEqualTo("/var/run/zuul=.sock");
    }

    @Test
    void failsOnMissingEqual() {
        assertThatThrownBy(() -> {
                    SocketAddressProperty.Decoder.INSTANCE.apply("ANY");
                })
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failsOnBadPort() {
        for (BindType type : Arrays.asList(
                BindType.ANY,
                BindType.IPV4_ANY,
                BindType.IPV6_ANY,
                BindType.ANY_LOCAL,
                BindType.IPV4_LOCAL,
                BindType.IPV6_LOCAL)) {
            assertThatThrownBy(() -> {
                        SocketAddressProperty.Decoder.INSTANCE.apply(type.name() + "=bogus");
                    })
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Port");
        }
    }

    @Test
    public void failsOnBadAddress() throws Exception {
        assertThatThrownBy(() -> {
                    SocketAddressProperty.Decoder.INSTANCE.apply("");
                })
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid address");
    }
}
