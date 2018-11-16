package com.netflix.zuul.sample.push;

import com.netflix.config.CachedDynamicIntProperty;
import com.netflix.zuul.netty.server.push.PushClientProtocolHandler;
import com.netflix.zuul.netty.server.push.PushConnectionRegistry;
import com.netflix.zuul.netty.server.push.PushProtocol;
import com.netflix.zuul.netty.server.push.PushRegistrationHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.*;

import java.util.concurrent.ThreadLocalRandom;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Created by saroskar on 10/10/16.
 */
public class SampleSSEPushClientProtocolHandler extends PushClientProtocolHandler {

    public static final CachedDynamicIntProperty SSE_RETRY_BASE_INTERVAL = new CachedDynamicIntProperty("zuul.push.sse.retry.base", 5000);

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object mesg) throws Exception {
        if (mesg instanceof  FullHttpRequest) {
            final FullHttpRequest req = (FullHttpRequest) mesg;
            if ((req.method() == HttpMethod.GET) && (PushProtocol.SSE.getPath().equals(req.uri()))) {
                ctx.pipeline().fireUserEventTriggered(PushProtocol.SSE.getHandshakeCompleteEvent());

                final DefaultHttpResponse resp = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.OK);
                final HttpHeaders headers = resp.headers();
                headers.add("Connection", "keep-alive");
                headers.add("Content-Type", "text/event-stream");
                headers.add("Transfer-Encoding", "chunked");

                final ChannelFuture cf = ctx.channel().writeAndFlush(resp);
                cf.addListener(future -> {
                    if (future.isSuccess()) {
                        ChannelPipeline pipeline = ctx.pipeline();
                        if (pipeline.get(HttpObjectAggregator.class) != null) {
                            pipeline.remove(HttpObjectAggregator.class);
                        }
                        if (pipeline.get(HttpContentCompressor.class) != null) {
                            pipeline.remove(HttpContentCompressor.class);
                        }
                        final String reconnetInterval = "retry: " + SSE_RETRY_BASE_INTERVAL.get() + "\r\n\r\n";
                        ctx.writeAndFlush(reconnetInterval);
                    }
                });
            }
        }
    }

}