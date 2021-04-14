package com.netflix.netty.common;

import com.google.common.truth.Truth;
import com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteEvent;
import com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteReason;
import com.netflix.netty.common.HttpLifecycleChannelHandler.State;
import com.netflix.netty.common.HttpServerLifecycleChannelHandler.HttpServerLifecycleOutboundChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

public class HttpServerLifecycleChannelHandlerTest {

    final class AssertReasonHandler extends ChannelInboundHandlerAdapter {

        CompleteEvent completeEvent;

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            assert evt instanceof CompleteEvent;
            this.completeEvent = (CompleteEvent) evt;
        }

        public CompleteEvent getCompleteEvent() {
            return completeEvent;
        }
    }

    @Test
    public void completionEventReasonIsUpdatedOnPipelineReject() {

        final EmbeddedChannel channel = new EmbeddedChannel(new HttpServerLifecycleOutboundChannelHandler());
        final AssertReasonHandler reasonHandler = new AssertReasonHandler();
        channel.pipeline().addLast(reasonHandler);

        channel.attr(HttpLifecycleChannelHandler.ATTR_STATE).set(State.STARTED);
        // emulate pipeline rejection
        channel.attr(HttpLifecycleChannelHandler.ATTR_HTTP_PIPELINE_REJECT).set(Boolean.TRUE);
        // Fire close
        channel.pipeline().close();

        Truth.assertThat(reasonHandler.getCompleteEvent().getReason()).isEqualTo(CompleteReason.PIPELINE_REJECT);
    }

    @Test
    public void completionEventReasonIsCloseByDefault() {

        final EmbeddedChannel channel = new EmbeddedChannel(new HttpServerLifecycleOutboundChannelHandler());
        final AssertReasonHandler reasonHandler = new AssertReasonHandler();
        channel.pipeline().addLast(reasonHandler);

        channel.attr(HttpLifecycleChannelHandler.ATTR_STATE).set(State.STARTED);
        // Fire close
        channel.pipeline().close();

        Truth.assertThat(reasonHandler.getCompleteEvent().getReason()).isEqualTo(CompleteReason.CLOSE);
    }
}
