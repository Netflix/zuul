package com.netflix.netty.common;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * Just listens for the IdleStateEvent and closes the channel if received.
 */
public class CloseOnIdleStateHandler extends ChannelInboundHandlerAdapter
{
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception
    {
        super.userEventTriggered(ctx, evt);

        if (evt instanceof IdleStateEvent) {
            ctx.close();
        }
    }
}
