/*
 * Copyright 2024 Netflix, Inc.
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

/*
 * @author Argha C
 * @since 10/2/24
 */
import java.net.SocketAddress;
import java.util.Objects;

/**
 * A specification of an address to listen on.
 */
public record ListenerSpec(String addressName, boolean defaultAddressEnabled, SocketAddress defaultAddressValue) {

    public ListenerSpec {
        Objects.requireNonNull(addressName, "addressName");
        Objects.requireNonNull(defaultAddressValue, "defaultAddressValue");
    }

    /**
     * The fast property name that indicates if this address is enabled.  This is used when overriding
     * {@link #defaultAddressEnabled}.
     */
    public String addressEnabledPropertyName() {
        return "zuul.server." + addressName + ".enabled";
    }

    /**
     * The fast property to override the default port for the address name
     */
    @Deprecated
    public String portPropertyName() {
        return "zuul.server.port." + addressName;
    }

    /**
     * The fast property to override the default address name
     */
    public String addressPropertyName() {
        return "zuul.server.addr." + addressName;
    }
}
