package com.netflix.zuul.netty.push;

import com.google.common.base.Charsets;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import com.netflix.zuul.netty.push.PushConnectionRegistry.PushConnection;

/**
 * Created by saroskar on 10/10/16.
 */
public enum PushProtocol {

    WEBSOCKET {
        @Override
        public Object getHandshakeCompleteEvent() {
            return WebSocketServerProtocolHandler.ServerHandshakeStateEvent.HANDSHAKE_COMPLETE;
        }

        @Override
        public String getPath() {
            return "/ws";
        }

        @Override
        public ChannelFuture sendPushMessage(ChannelHandlerContext ctx, ByteBuf mesg) {
            final TextWebSocketFrame wsf = new TextWebSocketFrame(mesg);
            return ctx.channel().writeAndFlush(wsf);
        }

        @Override
        public ChannelFuture sendPing(ChannelHandlerContext ctx) {
            return ctx.channel().writeAndFlush(new PingWebSocketFrame());
        }
    },

    SSE {
        private static final String SSE_HANDSHAKE_COMPLETE_EVENT = "sse_handshake_complete";

        @Override
        public Object getHandshakeCompleteEvent() {
            return SSE_HANDSHAKE_COMPLETE_EVENT;
        }

        @Override
        public String getPath() {
            return "/sse";
        }

        private static final String SSE_PREAMBLE = "event: push\r\ndata: ";
        private static final String SSE_TERMINATION = "\r\n\r\n";

        @Override
        public ChannelFuture sendPushMessage(ChannelHandlerContext ctx, ByteBuf mesg) {
            final ByteBuf newBuff = ctx.alloc().buffer();
            newBuff.ensureWritable(SSE_PREAMBLE.length());
            newBuff.writeCharSequence(SSE_PREAMBLE, Charsets.UTF_8);
            newBuff.ensureWritable(mesg.writableBytes());
            newBuff.writeBytes(mesg);
            newBuff.ensureWritable(SSE_TERMINATION.length());
            newBuff.writeCharSequence(SSE_TERMINATION, Charsets.UTF_8);
            mesg.release();
            return ctx.channel().writeAndFlush(newBuff);
        }

        private static final String  SSE_PING = "event: ping\r\ndata: ping\r\n\r\n";

        @Override
        public ChannelFuture sendPing(ChannelHandlerContext ctx) {
            final ByteBuf newBuff = ctx.alloc().buffer();
            newBuff.ensureWritable(SSE_PING.length());
            newBuff.writeCharSequence(SSE_PING, Charsets.UTF_8);
            return ctx.channel().writeAndFlush(newBuff);
        }
    };

    public abstract Object getHandshakeCompleteEvent();
    public abstract String getPath();
    public abstract ChannelFuture sendPushMessage(ChannelHandlerContext ctx, ByteBuf mesg);
    public abstract ChannelFuture sendPing(ChannelHandlerContext ctx);

}
