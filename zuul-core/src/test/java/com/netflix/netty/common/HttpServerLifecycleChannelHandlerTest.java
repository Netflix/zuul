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

package com.netflix.netty.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteEvent;
import com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteReason;
import com.netflix.netty.common.HttpLifecycleChannelHandler.State;
import com.netflix.netty.common.HttpServerLifecycleChannelHandler.HttpServerLifecycleInboundChannelHandler;
import com.netflix.netty.common.HttpServerLifecycleChannelHandler.HttpServerLifecycleOutboundChannelHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCountUtil;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HttpServerLifecycleChannelHandlerTest {

    private static final class CompleteEventCollector extends ChannelInboundHandlerAdapter {
        final List<CompleteEvent> events = new ArrayList<>();

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof CompleteEvent ce) {
                events.add(ce);
            }
            super.userEventTriggered(ctx, evt);
        }
    }

    final class AssertReasonHandler extends ChannelInboundHandlerAdapter {

        CompleteEvent completeEvent;

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            assertThat(evt).isInstanceOf(CompleteEvent.class);
            this.completeEvent = (CompleteEvent) evt;
        }

        public CompleteEvent getCompleteEvent() {
            return completeEvent;
        }
    }

    @Test
    void completionEventReasonIsUpdatedOnPipelineReject() {

        EmbeddedChannel channel = new EmbeddedChannel(new HttpServerLifecycleOutboundChannelHandler());
        AssertReasonHandler reasonHandler = new AssertReasonHandler();
        channel.pipeline().addLast(reasonHandler);

        channel.attr(HttpLifecycleChannelHandler.ATTR_STATE).set(State.STARTED);
        // emulate pipeline rejection
        channel.attr(HttpLifecycleChannelHandler.ATTR_HTTP_PIPELINE_REJECT).set(true);
        // Fire close
        channel.pipeline().close();

        assertThat(reasonHandler.getCompleteEvent().getReason()).isEqualTo(CompleteReason.PIPELINE_REJECT);
    }

    @Test
    void completionEventReasonIsCloseByDefault() {

        EmbeddedChannel channel = new EmbeddedChannel(new HttpServerLifecycleOutboundChannelHandler());
        AssertReasonHandler reasonHandler = new AssertReasonHandler();
        channel.pipeline().addLast(reasonHandler);

        channel.attr(HttpLifecycleChannelHandler.ATTR_STATE).set(State.STARTED);
        // Fire close
        channel.pipeline().close();

        assertThat(reasonHandler.getCompleteEvent().getReason()).isEqualTo(CompleteReason.CLOSE);
    }

    @Test
    void sessionCompleteNotFiredAfterForwarded1xxAsTwoMessages() {
        // A 1xx forwarded from origin arrives as two separate pipeline objects:
        // an HttpResponse(103) and a trailing empty LastHttpContent.
        CompleteEventCollector collector = new CompleteEventCollector();
        EmbeddedChannel channel = new EmbeddedChannel(new HttpServerLifecycleOutboundChannelHandler(), collector);
        channel.attr(HttpLifecycleChannelHandler.ATTR_STATE).set(State.STARTED);

        channel.writeOutbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.EARLY_HINTS));
        channel.writeOutbound(LastHttpContent.EMPTY_LAST_CONTENT);

        assertThat(collector.events).isEmpty();
        channel.finishAndReleaseAll();
    }

    @Test
    void sessionCompleteNotFiredAfterFullHttpResponse100() {
        // Zuul-generated 100 Continue arrives as a FullHttpResponse (both HttpResponse and LastHttpContent).
        CompleteEventCollector collector = new CompleteEventCollector();
        EmbeddedChannel channel = new EmbeddedChannel(new HttpServerLifecycleOutboundChannelHandler(), collector);
        channel.attr(HttpLifecycleChannelHandler.ATTR_STATE).set(State.STARTED);

        channel.writeOutbound(
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE, Unpooled.EMPTY_BUFFER));

        assertThat(collector.events).isEmpty();
        channel.finishAndReleaseAll();
    }

    @Test
    void sessionCompleteFiredAfterFinalResponseFollowing1xx() {
        CompleteEventCollector collector = new CompleteEventCollector();
        EmbeddedChannel channel = new EmbeddedChannel(new HttpServerLifecycleOutboundChannelHandler(), collector);
        channel.attr(HttpLifecycleChannelHandler.ATTR_STATE).set(State.STARTED);

        // Interim 1xx pair — must NOT fire SESSION_COMPLETE
        channel.writeOutbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.EARLY_HINTS));
        channel.writeOutbound(LastHttpContent.EMPTY_LAST_CONTENT);
        assertThat(collector.events).isEmpty();

        // Final 200 — must fire SESSION_COMPLETE exactly once
        channel.writeOutbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
        channel.writeOutbound(new DefaultLastHttpContent());
        assertThat(collector.events).hasSize(1);
        assertThat(collector.events.get(0).getReason()).isEqualTo(CompleteReason.SESSION_COMPLETE);
        channel.finishAndReleaseAll();
    }

    @Test
    void sessionCompleteFiredForNormalFinalResponse() {
        CompleteEventCollector collector = new CompleteEventCollector();
        EmbeddedChannel channel = new EmbeddedChannel(new HttpServerLifecycleOutboundChannelHandler(), collector);
        channel.attr(HttpLifecycleChannelHandler.ATTR_STATE).set(State.STARTED);

        channel.writeOutbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
        channel.writeOutbound(new DefaultLastHttpContent());

        assertThat(collector.events).hasSize(1);
        assertThat(collector.events.get(0).getReason()).isEqualTo(CompleteReason.SESSION_COMPLETE);
        channel.finishAndReleaseAll();
    }

    @Test
    void pipelineRejectReleasesIfNeeded() {

        EmbeddedChannel channel = new EmbeddedChannel(new HttpServerLifecycleInboundChannelHandler());

        ByteBuf buffer = UnpooledByteBufAllocator.DEFAULT.buffer();
        try {
            assertThat(buffer.refCnt()).isEqualTo(1);
            FullHttpRequest httpRequest =
                    new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/whatever", buffer);
            channel.attr(HttpLifecycleChannelHandler.ATTR_STATE).set(State.STARTED);
            channel.writeInbound(httpRequest);

            assertThat(channel.attr(HttpLifecycleChannelHandler.ATTR_HTTP_PIPELINE_REJECT)
                            .get())
                    .isEqualTo(true);
            assertThat(buffer.refCnt()).isEqualTo(0);
        } finally {
            if (buffer.refCnt() != 0) {
                ReferenceCountUtil.release(buffer);
            }
        }
    }
}
