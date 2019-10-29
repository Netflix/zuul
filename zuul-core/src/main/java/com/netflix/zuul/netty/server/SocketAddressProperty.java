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

import com.google.common.annotations.VisibleForTesting;
import com.netflix.config.StringDerivedProperty;
import io.netty.channel.unix.DomainSocketAddress;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * This class expresses an address that Zuul can bind to.  Similar to {@link
 * com.netflix.config.DynamicStringMapProperty} this class uses a similar key=value syntax, but only supports a single
 * pair.
 *
 * <p>To use this class, set a bind type such as {@link BindType#ANY} and assign it a port number like {@code 7001}.
 *     Sample usage:
 *     <ul>
 *         <li>{@code =7001} - equivalent to {@code ANY=7001}</li>
 *         <li>{@code ANY=7001} Binds on all IP addresses and IP stack for port 7001</li>
 *         <li>{@code IPV4_ANY=7001} Binds on all IPv4 address 0.0.0.0 for port 7001</li>
 *         <li>{@code IPV6_ANY=7001} Binds on all IPv6 address :: for port 7001</li>
 *         <li>{@code ANY_LOCAL=7001} Binds on localhost for all IP stacks for port 7001</li>
 *         <li>{@code IPV4_LOCAL=7001} Binds on IPv4 localhost (127.0.0.1) for port 7001</li>
 *         <li>{@code IPV6_LOCAL=7001} Binds on IPv6 localhost (::1) for port 7001</li>
 *         <li>{@code UDS=/var/run/zuul.sock} Binds a domain socket at /var/run/zuul.sock</li>
 *     </ul>
 *
 *  <p>Note that the local IPv4 binds only work for {@code 127.0.0.1}, and not any other loopback addresses.  Currently,
 *      all IP stack specific bind types only "prefer" a stack; it is up to the OS and the JVM to pick the the final
 *      address.
 */
public final class SocketAddressProperty extends StringDerivedProperty<SocketAddress> {

    public enum BindType {
        /**
         * Supports any IP stack, for a given port.  This is the default behaviour.  This also indicates that the
         * caller doesn't prefer a given IP stack.
         */
        ANY,
        /**
         * Binds on IPv4 {@code 0.0.0.0} address.
         */
        IPV4_ANY(() -> InetAddress.getByAddress("0.0.0.0", new byte[] {0, 0, 0, 0})),
        /**
         * Binds on IPv6 {@code ::} address.
         */
        IPV6_ANY(() -> InetAddress.getByAddress("::", new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0})),
        /**
         * Binds on any local address. This indicates that the caller doesn't prefer a given IP stack.
         */
        ANY_LOCAL(InetAddress::getLoopbackAddress),
        /**
         * Binds on the IPv4 {@code 127.0.0.1} localhost address.
         */
        IPV4_LOCAL(() -> InetAddress.getByAddress("localhost", new byte[] {127, 0, 0, 1})),
        /**
         * Binds on the IPv6 {@code ::1} localhost address.
         */
        IPV6_LOCAL(() ->
                InetAddress.getByAddress("localhost", new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1})),
        /**
         * Binds on the Unix Domain Socket path.
         */
        UDS,
        ;

        @Nullable
        private final Supplier<? extends InetAddress> addressSupplier;

        BindType() {
            addressSupplier = null;
        }

        BindType(Callable<? extends InetAddress> addressFn) {
            this.addressSupplier = () -> {
                try {
                    return addressFn.call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
        }
    }

    @VisibleForTesting
    static final class Decoder implements com.google.common.base.Function<String, SocketAddress> {

        static final Decoder INSTANCE = new Decoder();

        @Override
        public SocketAddress apply(String input) {
            if (input == null || input.isEmpty()) {
                throw new IllegalArgumentException("Invalid address");
            }

            int equalsPosition = input.indexOf('=');
            if (equalsPosition == -1) {
                throw new IllegalArgumentException("Invalid address " + input);
            }
            String rawBindType = equalsPosition != 0 ? input.substring(0, equalsPosition) : BindType.ANY.name();
            BindType bindType = BindType.valueOf(rawBindType.toUpperCase(Locale.ROOT));
            String rawAddress = input.substring(equalsPosition + 1);
            int port;
            parsePort: {
                switch (bindType) {
                    case ANY: // fallthrough
                    case IPV4_ANY: // fallthrough
                    case IPV6_ANY: // fallthrough
                    case ANY_LOCAL: // fallthrough
                    case IPV4_LOCAL: // fallthrough
                    case IPV6_LOCAL: // fallthrough
                        try {
                            port = Integer.parseInt(rawAddress);
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("Invalid Port " + input, e);
                        }
                        break parsePort;
                    case UDS:
                        port = -1;
                        break parsePort;
                }
                throw new AssertionError("Missed cased: " + bindType);
            }

            switch (bindType) {
                case ANY:
                    return new InetSocketAddress(port);
                case IPV4_ANY: // fallthrough
                case IPV6_ANY: // fallthrough
                case ANY_LOCAL: // fallthrough
                case IPV4_LOCAL: // fallthrough
                case IPV6_LOCAL: // fallthrough
                    return new InetSocketAddress(bindType.addressSupplier.get(), port);
                case UDS:
                    return new DomainSocketAddress(rawAddress);
            }
            throw new AssertionError("Missed cased: " + bindType);
        }

        @Override
        public boolean equals(Object object) {
            return false;
        }
    }

    public SocketAddressProperty(String propName, SocketAddress defaultValue) {
        super(propName, defaultValue, Decoder.INSTANCE);
    }

    public SocketAddressProperty(String propName, String defaultValue) {
        this(propName, Decoder.INSTANCE.apply(defaultValue));
    }
}
