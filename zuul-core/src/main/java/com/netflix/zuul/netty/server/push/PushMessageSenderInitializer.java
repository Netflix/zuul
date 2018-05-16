package com.netflix.zuul.netty.server.push;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

/**
 * Author: Susheel Aroskar
 * Date: 5/16/18
 */
public abstract class PushMessageSenderInitializer extends ChannelInitializer<Channel> {

    private final PushConnectionRegistry pushConnectionRegistry;

    public PushMessageSenderInitializer(PushConnectionRegistry pushConnectionRegistry) {
        this.pushConnectionRegistry = pushConnectionRegistry;
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        final ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new HttpObjectAggregator(65536));
        pipeline.addLast(getPushMessageSender(pushConnectionRegistry));
    }

    protected abstract PushMessageSender getPushMessageSender(PushConnectionRegistry pushConnectionRegistry);

}
