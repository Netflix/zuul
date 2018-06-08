package com.netflix.zuul.sample.push;

import com.netflix.config.CachedDynamicIntProperty;
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
public class SampleSSEPushRegistrationHandler extends PushRegistrationHandler {

    public static final CachedDynamicIntProperty SSE_RETRY_BASE_INTERVAL = new CachedDynamicIntProperty("zuul.push.sse.retry.base", 5000);
    public static final CachedDynamicIntProperty SSE_RETRY_RANDOM_RANGE = new CachedDynamicIntProperty("zuul.push.sse.retry.random.range", 5000);

    public SampleSSEPushRegistrationHandler(PushConnectionRegistry pushConnectionRegistry) {
        super(pushConnectionRegistry, PushProtocol.SSE);
    }

    @Override
    protected void handleRead(final ChannelHandlerContext ctx, Object mesg) {
        if (mesg instanceof  FullHttpRequest) {
            final FullHttpRequest req = (FullHttpRequest) mesg;
            if ((req.method() == HttpMethod.GET) && (pushProtocol.getPath().equals(req.uri()))) {
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
                        final String reconnetInterval = "retry: " + Integer.toString(getRetryInterval()) + "\r\n\r\n";
                        ctx.writeAndFlush(reconnetInterval);
                    }
                });
            }
        }
    }

    private int getRetryInterval() {
        return SSE_RETRY_BASE_INTERVAL.get() + ThreadLocalRandom.current().nextInt(SSE_RETRY_RANDOM_RANGE.get());
    }

    @Override
    protected Object goAwayMessage() {
        return "event: goaway\r\ndata: _CLOSE_\r\n\r\n";
    }

    @Override
    protected Object serverClosingConnectionMessage(int statusCode, String reasonText) {
        return "event: close\r\ndata: " + statusCode + " " + reasonText + "\r\n\r\n";
    }

}