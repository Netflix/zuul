/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.netty.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;

class SslExceptionsHandlerTest {

    private final Registry registry = new DefaultRegistry();

    @Test
    void swallowsSslExceptionWrappedInDecoderException() {
        EmbeddedChannel channel = new EmbeddedChannel(new SslExceptionsHandler(registry));

        channel.pipeline()
                .fireExceptionCaught(new DecoderException(
                        new SSLException("error:1e000065:Cipher functions:OPENSSL_internal:BAD_DECRYPT")));

        assertThatCode(channel::checkException).doesNotThrowAnyException();
        assertThat(swallowedCount("SSLException")).isEqualTo(1);
    }

    @Test
    void swallowsTopLevelSslException() {
        EmbeddedChannel channel = new EmbeddedChannel(new SslExceptionsHandler(registry));

        channel.pipeline().fireExceptionCaught(new SSLException("not an SSL/TLS record"));

        assertThatCode(channel::checkException).doesNotThrowAnyException();
        assertThat(swallowedCount("SSLException")).isEqualTo(1);
    }

    @Test
    void swallowsHandshakeExceptionTaggedByType() {
        EmbeddedChannel channel = new EmbeddedChannel(new SslExceptionsHandler(registry));

        channel.pipeline().fireExceptionCaught(new DecoderException(new SSLHandshakeException("no cipher overlap")));

        assertThatCode(channel::checkException).doesNotThrowAnyException();
        assertThat(swallowedCount("SSLHandshakeException")).isEqualTo(1);
    }

    @Test
    void swallowsDeeplyNestedSslException() {
        EmbeddedChannel channel = new EmbeddedChannel(new SslExceptionsHandler(registry));

        channel.pipeline()
                .fireExceptionCaught(
                        new RuntimeException("outer", new DecoderException(new SSLException("WRONG_VERSION_NUMBER"))));

        assertThatCode(channel::checkException).doesNotThrowAnyException();
        assertThat(swallowedCount("SSLException")).isEqualTo(1);
    }

    @Test
    void propagatesNonSslException() {
        EmbeddedChannel channel = new EmbeddedChannel(new SslExceptionsHandler(registry));

        channel.pipeline().fireExceptionCaught(new IllegalStateException("boom"));

        assertThatThrownBy(channel::checkException).isInstanceOf(IllegalStateException.class);
        assertThat(swallowedCount("SSLException")).isZero();
    }

    private long swallowedCount(String cause) {
        return registry.counter("server.ssl.exception.swallowed", "cause", cause)
                .count();
    }
}
