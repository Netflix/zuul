/*
 * Copyright 2021 Netflix, Inc.
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

import java.net.SocketAddress;
import java.util.Objects;
import javax.annotation.CheckReturnValue;

public final class NamedSocketAddress extends SocketAddress {

    private final String name;
    private final SocketAddress delegate;

    public NamedSocketAddress(String name, SocketAddress delegate) {
        this.name = Objects.requireNonNull(name);
        this.delegate = Objects.requireNonNull(delegate);
    }

    public String name() {
        return name;
    }

    public SocketAddress unwrap() {
        return delegate;
    }

    @CheckReturnValue
    public NamedSocketAddress withNewSocket(SocketAddress delegate) {
        return new NamedSocketAddress(this.name, delegate);
    }

    @Override
    public String toString() {
        return "NamedSocketAddress{" +
                "name='" + name + '\'' +
                ", delegate=" + delegate +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NamedSocketAddress that = (NamedSocketAddress) o;
        return Objects.equals(name, that.name) && Objects.equals(delegate, that.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, delegate);
    }
}
