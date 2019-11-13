/*
 * Copyright 2016 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */

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