package com.netflix.zuul.netty.server.http2;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame;
import io.netty.handler.codec.http2.Http2Error;
import java.util.List;

/**
 * This class is only suitable for use on HTTP/2 child channels.
 */
public final class Http2ContentLengthEnforcingHandler extends ChannelInboundHandlerAdapter {
    private static final long UNSET_CONTENT_LENGTH = -1;

    private long expectedContentLength = UNSET_CONTENT_LENGTH;

    private long seenContentLength;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest req = (HttpRequest) msg;
            List<String> lengthHeaders = req.headers().getAll(HttpHeaderNames.CONTENT_LENGTH);
            if (lengthHeaders.size() > 1) {
                ctx.writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.PROTOCOL_ERROR));
                return;
            } else if (lengthHeaders.size() == 1) {
                expectedContentLength = Long.parseLong(lengthHeaders.get(0));
                if (expectedContentLength < 0) {
                    // TODO(carl-mastrangelo): this is not right, but meh.  Fix this to return a proper 400.
                    ctx.writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.PROTOCOL_ERROR));
                    return;
                }
            }
            if (expectedContentLength != UNSET_CONTENT_LENGTH && HttpUtil.isTransferEncodingChunked(req)) {
                // TODO(carl-mastrangelo): this is not right, but meh.  Fix this to return a proper 400.
                ctx.writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.PROTOCOL_ERROR));
                return;
            }
        }
        if (msg instanceof HttpContent) {
            ByteBuf content = ((HttpContent) msg).content();
            seenContentLength = Math.addExact(seenContentLength, content.readableBytes());
            if (expectedContentLength != UNSET_CONTENT_LENGTH && seenContentLength > expectedContentLength) {
                // TODO(carl-mastrangelo): this is not right, but meh.  Fix this to return a proper 400.
                ctx.writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.PROTOCOL_ERROR));
                return;
            }
        }
        if (msg instanceof LastHttpContent) {
            if (expectedContentLength != UNSET_CONTENT_LENGTH && seenContentLength != expectedContentLength) {
                // TODO(carl-mastrangelo): this is not right, but meh.  Fix this to return a proper 400.
                ctx.writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.PROTOCOL_ERROR));
                return;
            }
        }
        super.channelRead(ctx, msg);
    }
}
