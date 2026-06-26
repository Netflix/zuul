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

package com.netflix.netty.common.close;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.netty.common.HttpLifecycleChannelHandler.StartEvent;
import com.netflix.netty.common.SourceAddressChannelHandler;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Http1ConnectionCloseHandlerTest {

    private static final int PORT = 7001;

    private Registry registry;
    private EmbeddedChannel channel;

    @BeforeEach
    void setup() {
        registry = new DefaultRegistry();
        channel = new EmbeddedChannel(new Http1ConnectionCloseHandler(registry));
        channel.attr(SourceAddressChannelHandler.ATTR_SERVER_LOCAL_PORT).set(PORT);
    }

    @AfterEach
    void cleanup() {
        channel.finishAndReleaseAll();
    }

    @Test
    void gracefulCloseEventWithNoRequestInFlightClosesImmediately() {
        channel.pipeline().fireUserEventTriggered(new ConnectionCloseEvent.Graceful(CloseReason.SHUTDOWN));

        assertThat(channel.isOpen()).isFalse();
        assertThat(closeCount("GRACEFUL", CloseReason.SHUTDOWN)).isEqualTo(1);
    }

    @Test
    void closeEventDeferredWhileRequestInFlightThenClosesAfterLastContent() {
        channel.pipeline()
                .fireUserEventTriggered(
                        new StartEvent(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/")));
        channel.pipeline().fireUserEventTriggered(new ConnectionCloseEvent.Graceful(CloseReason.SHUTDOWN));

        assertThat(channel.isOpen())
                .as("close is deferred until the in-flight response completes")
                .isTrue();

        channel.writeOutbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
        HttpResponse written = channel.readOutbound();
        assertThat(written.headers().get(HttpHeaderNames.CONNECTION)).isEqualTo("close");

        channel.writeOutbound(LastHttpContent.EMPTY_LAST_CONTENT);

        assertThat(channel.isOpen()).isFalse();
        assertThat(closeCount("GRACEFUL", CloseReason.SHUTDOWN)).isEqualTo(1);
    }

    @Test
    void gracefulDelayedCloseEventSchedulesClose() {
        channel.pipeline()
                .fireUserEventTriggered(
                        new ConnectionCloseEvent.GracefulDelayed(CloseReason.OUT_OF_SERVICE, Duration.ofMillis(1)));

        assertThat(channel.isOpen())
                .as("close should be deferred until the scheduled task runs")
                .isTrue();
        assertThat(closeCount("GRACEFUL_DELAYED", CloseReason.OUT_OF_SERVICE)).isZero();

        channel.runScheduledPendingTasks();

        assertThat(channel.isOpen()).isFalse();
        assertThat(closeCount("GRACEFUL_DELAYED", CloseReason.OUT_OF_SERVICE)).isEqualTo(1);
    }

    @Test
    void responseNotFlaggedForCloseIsUnmodified() {
        channel.writeOutbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));

        HttpResponse written = channel.readOutbound();
        assertThat(written.headers().contains(HttpHeaderNames.CONNECTION)).isFalse();
        assertThat(channel.isOpen()).isTrue();
    }

    private long closeCount(String closeType, CloseReason reason) {
        Id id = registry.createId("server.connection.close.handled")
                .withTag("close_type", closeType)
                .withTag("close_reason", reason.name())
                .withTag("port", Integer.toString(PORT))
                .withTag("protocol", "http/1.1");
        return registry.counter(id).count();
    }
}
