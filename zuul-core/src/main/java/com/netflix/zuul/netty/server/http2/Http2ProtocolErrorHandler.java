package com.netflix.zuul.netty.server.http2;

import com.netflix.zuul.netty.SpectatorUtils;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http2.DefaultHttp2GoAwayFrame;
import io.netty.handler.codec.http2.Http2Exception;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles exceptions due to malformed http/2 requests by sending a go-away and closing the connection
 *
 * See {@link com.netflix.netty.common.channel.config.CommonChannelConfigKeys#http2CloseOnCodecErrors}
 * @author Justin Guerra
 * @since 11/14/23
 */
public class Http2ProtocolErrorHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(Http2ProtocolErrorHandler.class);

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if(cause instanceof Http2Exception http2Exception && http2Exception.shutdownHint() == Http2Exception.ShutdownHint.HARD_SHUTDOWN) {
            LOG.debug("Http/2 protocol error. Closing connection", cause);
            SpectatorUtils.newCounter("zuul.http2.protocol.close", http2Exception.getClass().getSimpleName()).increment();
            ctx.writeAndFlush(new DefaultHttp2GoAwayFrame(http2Exception.error()))
                    .addListener(ChannelFutureListener.CLOSE);
        } else {
            ctx.fireExceptionCaught(cause);
        }
    }
}
