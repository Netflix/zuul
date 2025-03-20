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

import com.google.common.truth.Truth;
import com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteEvent;
import com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteReason;
import com.netflix.netty.common.HttpLifecycleChannelHandler.State;
import com.netflix.netty.common.HttpServerLifecycleChannelHandler.HttpServerLifecycleInboundChannelHandler;
import com.netflix.netty.common.HttpServerLifecycleChannelHandler.HttpServerLifecycleOutboundChannelHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

class HttpServerLifecycleChannelHandlerTest {

    final class AssertReasonHandler extends ChannelInboundHandlerAdapter {

        CompleteEvent completeEvent;

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            Truth.assertThat(evt).isInstanceOf(CompleteEvent.class);
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
        channel.attr(HttpLifecycleChannelHandler.ATTR_HTTP_PIPELINE_REJECT).set(Boolean.TRUE);
        // Fire close
        channel.pipeline().close();

        Truth.assertThat(reasonHandler.getCompleteEvent().getReason()).isEqualTo(CompleteReason.PIPELINE_REJECT);
    }

    @Test
    void completionEventReasonIsCloseByDefault() {

        EmbeddedChannel channel = new EmbeddedChannel(new HttpServerLifecycleOutboundChannelHandler());
        AssertReasonHandler reasonHandler = new AssertReasonHandler();
        channel.pipeline().addLast(reasonHandler);

        channel.attr(HttpLifecycleChannelHandler.ATTR_STATE).set(State.STARTED);
        // Fire close
        channel.pipeline().close();

        Truth.assertThat(reasonHandler.getCompleteEvent().getReason()).isEqualTo(CompleteReason.CLOSE);
    }

    @Test
    void pipelineRejectReleasesIfNeeded() {

        EmbeddedChannel channel = new EmbeddedChannel(new HttpServerLifecycleInboundChannelHandler());

        ByteBuf buffer = UnpooledByteBufAllocator.DEFAULT.buffer();
        try {
            Truth.assertThat(buffer.refCnt()).isEqualTo(1);
            FullHttpRequest httpRequest =
                    new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/whatever", buffer);
            channel.attr(HttpLifecycleChannelHandler.ATTR_STATE).set(State.STARTED);
            channel.writeInbound(httpRequest);

            Truth.assertThat(channel.attr(HttpLifecycleChannelHandler.ATTR_HTTP_PIPELINE_REJECT)
                            .get())
                    .isEqualTo(Boolean.TRUE);
            Truth.assertThat(buffer.refCnt()).isEqualTo(0);
        } finally {
            if (buffer.refCnt() != 0) {
                ReferenceCountUtil.release(buffer);
            }
        }
    }
}
