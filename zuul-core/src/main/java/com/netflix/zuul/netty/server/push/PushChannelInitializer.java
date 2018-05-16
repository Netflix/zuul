package com.netflix.zuul.netty.server.push;

import com.netflix.netty.common.channel.config.ChannelConfig;
import com.netflix.zuul.netty.server.BaseZuulChannelInitializer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;

/**
 * Author: Susheel Aroskar
 * Date: 5/15/18
 */
public abstract class PushChannelInitializer extends BaseZuulChannelInitializer {


    public PushChannelInitializer(int port, ChannelConfig channelConfig, ChannelConfig channelDependencies,
                                  ChannelGroup channels) {

        super(port, channelConfig, channelDependencies, channels);
    }

    @Override
    protected void addHttp1Handlers(ChannelPipeline pipeline) {
        pipeline.addLast(HTTP_CODEC_HANDLER_NAME, new HttpServerCodec(
                MAX_INITIAL_LINE_LENGTH.get(),
                MAX_HEADER_SIZE.get(),
                MAX_CHUNK_SIZE.get(),
                false
        ));
        pipeline.addLast(new HttpObjectAggregator(8192));
    }

    protected void addPushHandlers(final ChannelPipeline pipeline) {
        pipeline.addLast(PushAuthHandler.NAME, getPushAuthHandler());
        pipeline.addLast(new WebSocketServerCompressionHandler());
        pipeline.addLast(new WebSocketServerProtocolHandler(PushProtocol.WEBSOCKET.getPath(), null, true));
        pipeline.addLast(getPushRegistrationHandler());
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        final ChannelPipeline pipeline = ch.pipeline();
        storeChannel(ch);
        addTcpRelatedHandlers(pipeline);
        addHttp1Handlers(pipeline);
        addPushHandlers(pipeline);
    }


    protected abstract PushAuthHandler getPushAuthHandler();
    protected abstract PushRegistrationHandler getPushRegistrationHandler();

}
