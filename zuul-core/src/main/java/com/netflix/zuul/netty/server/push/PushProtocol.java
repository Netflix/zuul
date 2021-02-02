/**
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul.netty.server.push;

import com.google.common.base.Charsets;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

/**
 * Created by saroskar on 10/10/16.
 */
public enum PushProtocol {

    WEBSOCKET {
        @Override
        // The alternative object for HANDSHAKE_COMPLETE is not publicly visible, so disable deprecation warnings.  In
        // the future, it may be possible to not fire this even and remove the suppression.
        @SuppressWarnings("deprecation")
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

        @Override
        public Object goAwayMessage() {
            return new TextWebSocketFrame("_CLOSE_");
        }

        @Override
        public Object serverClosingConnectionMessage(int statusCode, String reasonText) {
            return new CloseWebSocketFrame(statusCode, reasonText);
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

        @Override
        public Object goAwayMessage() {
            return "event: goaway\r\ndata: _CLOSE_\r\n\r\n";
        }

        @Override
        public Object serverClosingConnectionMessage(int statusCode, String reasonText) {
            return "event: close\r\ndata: " + statusCode + " " + reasonText + "\r\n\r\n";
        }

    };

    public final void sendErrorAndClose(ChannelHandlerContext ctx, int statusCode, String reasonText) {
        final Object mesg = serverClosingConnectionMessage(statusCode, reasonText);
        ctx.writeAndFlush(mesg).addListener(ChannelFutureListener.CLOSE);
    }

    public abstract Object getHandshakeCompleteEvent();
    public abstract String getPath();
    public abstract ChannelFuture sendPushMessage(ChannelHandlerContext ctx, ByteBuf mesg);
    public abstract ChannelFuture sendPing(ChannelHandlerContext ctx);
    /**
     * Application level protocol for asking client to close connection
     * @return WebSocketFrame which when sent to client will cause it to close the WebSocket
     */
    public abstract Object goAwayMessage();
    /**
     * Message server sends to the client just before it force closes connection from its side
     */
    public abstract Object serverClosingConnectionMessage(int statusCode, String reasonText);

}
