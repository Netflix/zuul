package com.netflix.zuul.netty.server.push;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * Author: Susheel Aroskar
 * Date: 11/2/2018
 */
public class PushClientProtocolHandler extends ChannelInboundHandlerAdapter {

    protected PushUserAuth authEvent;


    protected boolean isAuthenticated() {
        return (authEvent != null && authEvent.isSuccess());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof PushUserAuth) {
            authEvent = (PushUserAuth) evt;
        }
        super.userEventTriggered(ctx, evt);
    }

}
