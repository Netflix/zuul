package com.netflix.zuul.netty.server.http2;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame;
import io.netty.handler.codec.http2.Http2Error;

import static io.netty.handler.codec.http2.Http2Exception.*;

/**
 * Author: Susheel Aroskar
 * Date: 5/7/2018
 */
@ChannelHandler.Sharable
public class Http2StreamErrorHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof StreamException) {
            StreamException streamEx = (StreamException) cause;
            ctx.writeAndFlush(new DefaultHttp2ResetFrame(streamEx.error()));
        } else if (cause instanceof DecoderException) {
            ctx.writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.PROTOCOL_ERROR));
        } else {
            super.exceptionCaught(ctx, cause);
        }
    }
}
