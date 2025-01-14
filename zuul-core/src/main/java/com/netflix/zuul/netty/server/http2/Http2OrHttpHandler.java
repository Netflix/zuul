/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.zuul.netty.server.http2;

import com.netflix.netty.common.channel.config.ChannelConfig;
import com.netflix.netty.common.channel.config.CommonChannelConfigKeys;
import com.netflix.netty.common.http2.DynamicHttp2FrameLogger;
import com.netflix.zuul.netty.server.BaseZuulChannelInitializer;
import com.netflix.zuul.netty.server.psk.TlsPskHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http2.DefaultHttp2RemoteFlowController;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.UniformStreamByteDistributor;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.util.AttributeKey;
import java.util.function.Consumer;

/**
 * Http2 Or Http Handler
 * <p>
 * Author: Arthur Gonigberg
 * Date: December 15, 2017
 */
public class Http2OrHttpHandler extends ApplicationProtocolNegotiationHandler {
    public static final AttributeKey<String> PROTOCOL_NAME = AttributeKey.valueOf("protocol_name");
    public static final String PROTOCOL_HTTP_1_1 = "HTTP/1.1";
    public static final String PROTOCOL_HTTP_2 = "HTTP/2";

    private static final String FALLBACK_APPLICATION_PROTOCOL = ApplicationProtocolNames.HTTP_1_1;

    private static final DynamicHttp2FrameLogger FRAME_LOGGER =
            new DynamicHttp2FrameLogger(LogLevel.DEBUG, Http2FrameCodec.class);

    private final ChannelHandler http2StreamHandler;
    private final int maxConcurrentStreams;
    private final int initialWindowSize;
    private final long maxHeaderTableSize;
    private final long maxHeaderListSize;
    private final boolean catchConnectionErrors;
    private final Consumer<ChannelPipeline> addHttpHandlerFn;

    public Http2OrHttpHandler(
            ChannelHandler http2StreamHandler,
            ChannelConfig channelConfig,
            Consumer<ChannelPipeline> addHttpHandlerFn) {
        super(FALLBACK_APPLICATION_PROTOCOL);
        this.http2StreamHandler = http2StreamHandler;
        this.maxConcurrentStreams = channelConfig.get(CommonChannelConfigKeys.maxConcurrentStreams);
        this.initialWindowSize = channelConfig.get(CommonChannelConfigKeys.initialWindowSize);
        this.maxHeaderTableSize = channelConfig.get(CommonChannelConfigKeys.maxHttp2HeaderTableSize);
        this.maxHeaderListSize = channelConfig.get(CommonChannelConfigKeys.maxHttp2HeaderListSize);
        this.catchConnectionErrors = channelConfig.get(CommonChannelConfigKeys.http2CatchConnectionErrors);
        this.addHttpHandlerFn = addHttpHandlerFn;
    }

    /**
     * this method is inspired by ApplicationProtocolNegotiationHandler.userEventTriggered
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof SslHandshakeCompletionEvent handshakeEvent) {
            if (handshakeEvent.isSuccess()) {
                TlsPskHandler tlsPskHandler = ctx.channel().pipeline().get(TlsPskHandler.class);
                if (tlsPskHandler != null) {
                    // PSK mode
                    try {
                        String tlsPskApplicationProtocol = tlsPskHandler.getApplicationProtocol();
                        configurePipeline(
                                ctx,
                                tlsPskApplicationProtocol != null
                                        ? tlsPskApplicationProtocol
                                        : FALLBACK_APPLICATION_PROTOCOL);
                    } catch (Throwable cause) {
                        exceptionCaught(ctx, cause);
                    } finally {
                        // Handshake failures are handled in exceptionCaught(...).
                        if (handshakeEvent.isSuccess()) {
                            removeSelfIfPresent(ctx);
                        }
                    }
                } else {
                    // non PSK mode
                    super.userEventTriggered(ctx, evt);
                }
            } else {
                // handshake failures
                // TODO sunnys - handle PSK handshake failures
                super.userEventTriggered(ctx, evt);
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    protected void configurePipeline(ChannelHandlerContext ctx, String protocol) throws Exception {
        if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
            ctx.channel().attr(PROTOCOL_NAME).set(PROTOCOL_HTTP_2);
            configureHttp2(ctx.pipeline());
            return;
        }
        if (ApplicationProtocolNames.HTTP_1_1.equals(protocol)) {
            ctx.channel().attr(PROTOCOL_NAME).set(PROTOCOL_HTTP_1_1);
            configureHttp1(ctx.pipeline());
            return;
        }

        throw new IllegalStateException("unknown protocol: " + protocol);
    }

    private void configureHttp2(ChannelPipeline pipeline) {

        // setup the initial stream settings for the server to use.
        Http2Settings settings = new Http2Settings()
                .maxConcurrentStreams(maxConcurrentStreams)
                .initialWindowSize(initialWindowSize)
                .headerTableSize(maxHeaderTableSize)
                .maxHeaderListSize(maxHeaderListSize);

        Http2FrameCodec frameCodec = Http2FrameCodecBuilder.forServer()
                .frameLogger(FRAME_LOGGER)
                .initialSettings(settings)
                .validateHeaders(true)
                .build();
        Http2Connection conn = frameCodec.connection();
        // Use the uniform byte distributor until https://github.com/netty/netty/issues/10525 is fixed.
        conn.remote()
                .flowController(new DefaultHttp2RemoteFlowController(conn, new UniformStreamByteDistributor(conn)));

        Http2MultiplexHandler multiplexHandler = new Http2MultiplexHandler(http2StreamHandler);

        // The frame codec MUST be in the pipeline.
        pipeline.addBefore("codec_placeholder", null, frameCodec);
        pipeline.replace("codec_placeholder", BaseZuulChannelInitializer.HTTP_CODEC_HANDLER_NAME, multiplexHandler);
        if (catchConnectionErrors) {
            pipeline.addLast(new Http2ConnectionErrorHandler());
        }
    }

    private void configureHttp1(ChannelPipeline pipeline) {
        addHttpHandlerFn.accept(pipeline);
    }

    private void removeSelfIfPresent(ChannelHandlerContext ctx) {
        ChannelPipeline pipeline = ctx.pipeline();
        if (!ctx.isRemoved()) {
            pipeline.remove(this);
        }
    }
}
