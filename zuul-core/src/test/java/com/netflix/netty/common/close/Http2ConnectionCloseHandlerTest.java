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

import com.netflix.netty.common.SourceAddressChannelHandler;
import com.netflix.netty.common.channel.config.ChannelConfig;
import com.netflix.netty.common.channel.config.CommonChannelConfigKeys;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.netty.server.BaseZuulChannelInitializer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2GoAwayFrame;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Http2ConnectionCloseHandlerTest {

    private static final int PORT = 7001;

    private Registry registry;
    private EmbeddedChannel channel;
    private RecordingExceptionHandler exceptionHandler;

    @BeforeEach
    void setup() {
        registry = new DefaultRegistry();
        exceptionHandler = new RecordingExceptionHandler();
        channel = new EmbeddedChannel(new Http2ConnectionCloseHandler(registry), exceptionHandler);
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
    void gracefulCloseEventSendsInitialGoAwayFrame() {
        channel.pipeline().fireUserEventTriggered(new ConnectionCloseEvent.Graceful(CloseReason.SHUTDOWN));

        Http2GoAwayFrame goaway = channel.readOutbound();
        assertThat(goaway.errorCode()).isEqualTo(Http2Error.NO_ERROR.code());
        assertThat(goaway.extraStreamIds()).isEqualTo(Integer.MAX_VALUE);
        assertThat(startedCount("GRACEFUL", CloseReason.SHUTDOWN)).isEqualTo(1);
        goaway.release();
    }

    @Test
    void gracefulDelayedCloseEventSendsGoAwayOnlyAfterScheduledTaskRuns() {
        channel.pipeline()
                .fireUserEventTriggered(
                        new ConnectionCloseEvent.GracefulDelayed(CloseReason.OUT_OF_SERVICE, Duration.ofMillis(1)));

        assertThat(channel.<Http2GoAwayFrame>readOutbound()).isNull();

        channel.runScheduledPendingTasks();

        Http2GoAwayFrame goaway = channel.readOutbound();
        assertThat(goaway).isNotNull();
        assertThat(startedCount("GRACEFUL_DELAYED", CloseReason.OUT_OF_SERVICE)).isEqualTo(1);
        goaway.release();
    }

    @Test
    void handleCloseEventIsANoOpWhenAlreadyFlaggedForClose() {
        channel.pipeline().fireUserEventTriggered(new ConnectionCloseEvent.Graceful(CloseReason.SHUTDOWN));
        Http2GoAwayFrame firstGoaway = channel.readOutbound();
        firstGoaway.release();

        channel.pipeline().fireUserEventTriggered(new ConnectionCloseEvent.Graceful(CloseReason.EXPIRATION));

        assertThat(channel.<Http2GoAwayFrame>readOutbound()).isNull();
        assertThat(startedCount("GRACEFUL", CloseReason.EXPIRATION)).isZero();
    }

    @Test
    void closeTimeoutFiresAnHttp2ExceptionRequestingGracefulShutdown() {
        channel.pipeline().fireUserEventTriggered(new ConnectionCloseEvent.Graceful(CloseReason.SHUTDOWN));
        Http2GoAwayFrame goaway = channel.readOutbound();
        goaway.release();

        channel.runScheduledPendingTasks();

        assertThat(exceptionHandler.caught).isInstanceOf(Http2Exception.class);
        Http2Exception h2e = (Http2Exception) exceptionHandler.caught;
        assertThat(h2e.error()).isEqualTo(Http2Error.NO_ERROR);
        assertThat(h2e.shutdownHint()).isEqualTo(Http2Exception.ShutdownHint.GRACEFUL_SHUTDOWN);
    }

    private long startedCount(String closeType, CloseReason reason) {
        Id id = registry.createId("server.connection.close.started")
                .withTag("close_type", closeType)
                .withTag("close_reason", reason.name())
                .withTag("port", Integer.toString(PORT))
                .withTag("protocol", "http/2");
        return registry.counter(id).count();
    }

    private static final class RecordingExceptionHandler extends ChannelInboundHandlerAdapter {

        private Throwable caught;

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            this.caught = cause;
        }
    }
}
