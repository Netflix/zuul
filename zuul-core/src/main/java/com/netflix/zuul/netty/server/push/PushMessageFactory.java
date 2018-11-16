package com.netflix.zuul.netty.server.push;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;

/**
 * Author: Susheel Aroskar
 * Date: 11/2/2018
 */
public abstract class PushMessageFactory {



    public final void sendErrorAndClose(ChannelHandlerContext ctx, int statusCode, String reasonText) {
        ctx.writeAndFlush(serverClosingConnectionMessage(statusCode, reasonText))
                .addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * Application level protocol for asking client to close connection
     * @return WebSocketFrame which when sent to client will cause it to close the WebSocket
     */
    protected abstract Object goAwayMessage();

    /**
     * Message server sends to the client just before it force closes connection from its side
     * @return
     */
    protected abstract Object serverClosingConnectionMessage(int statusCode, String reasonText);

}
