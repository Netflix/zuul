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

import com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteEvent;
import com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteReason;
import com.netflix.netty.common.HttpLifecycleChannelHandler.StartEvent;
import com.netflix.netty.common.SourceAddressChannelHandler;
import com.netflix.netty.common.channel.config.ChannelConfig;
import com.netflix.netty.common.channel.config.CommonChannelConfigKeys;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.netty.server.BaseZuulChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
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

        ChannelConfig channelConfig = new ChannelConfig();
        channelConfig.set(CommonChannelConfigKeys.connCloseDelay, 0);
        channel.attr(BaseZuulChannelInitializer.ATTR_CHANNEL_CONFIG).set(channelConfig);
    }

    @AfterEach
    void cleanup() {
        channel.finishAndReleaseAll();
    }

    @Test
    void handleCloseEventClosesImmediatelyWhenNoRequestInFlight() {
        channel.pipeline().fireUserEventTriggered(new ConnectionCloseEvent.Graceful(CloseReason.SHUTDOWN));

        assertThat(channel.isOpen()).isFalse();
        assertThat(closeCount("GRACEFUL", CloseReason.SHUTDOWN, "idle")).isEqualTo(1);
    }

    @Test
    void closeIsDeferredUntilLastHttpContentIsWrittenWhileRequestInFlight() {
        fireStartEvent();
        channel.pipeline().fireUserEventTriggered(new ConnectionCloseEvent.Graceful(CloseReason.SHUTDOWN));

        assertThat(channel.isOpen())
                .as("close deferred while a response is in flight")
                .isTrue();

        channel.writeOutbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
        channel.readOutbound();
        channel.writeOutbound(LastHttpContent.EMPTY_LAST_CONTENT);

        assertThat(channel.isOpen()).isFalse();
        assertThat(closeCount("GRACEFUL", CloseReason.SHUTDOWN, "timeout")).isEqualTo(1);
    }

    @Test
    void completeEventResetsInFlightTrackingSoALaterCloseEventClosesImmediately() {
        HttpRequest request = fireStartEvent();
        channel.pipeline()
                .fireUserEventTriggered(new CompleteEvent(
                        CompleteReason.SESSION_COMPLETE,
                        request,
                        new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)));

        channel.pipeline().fireUserEventTriggered(new ConnectionCloseEvent.Graceful(CloseReason.SHUTDOWN));

        assertThat(channel.isOpen()).isFalse();
        assertThat(closeCount("GRACEFUL", CloseReason.SHUTDOWN, "idle")).isEqualTo(1);
    }

    @Test
    void writeSetsConnectionCloseHeaderOnResponseWhenFlaggedForClose() {
        fireStartEvent();
        channel.pipeline().fireUserEventTriggered(new ConnectionCloseEvent.Graceful(CloseReason.SHUTDOWN));

        channel.writeOutbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
        HttpResponse written = channel.readOutbound();

        assertThat(written.headers().get(HttpHeaderNames.CONNECTION)).isEqualTo("close");
    }

    @Test
    void writeLeavesResponseUnmodifiedWhenNotFlaggedForClose() {
        channel.writeOutbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));

        HttpResponse written = channel.readOutbound();
        assertThat(written.headers().contains(HttpHeaderNames.CONNECTION)).isFalse();
        assertThat(channel.isOpen()).isTrue();
    }

    private HttpRequest fireStartEvent() {
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        channel.pipeline().fireUserEventTriggered(new StartEvent(request));
        return request;
    }

    private long closeCount(String closeType, CloseReason reason, String trigger) {
        Id id = registry.createId("server.connection.close.handled")
                .withTag("close_type", closeType)
                .withTag("close_reason", reason.name())
                .withTag("close_trigger", trigger)
                .withTag("port", Integer.toString(PORT))
                .withTag("protocol", "http/1.1");
        return registry.counter(id).count();
    }
}
