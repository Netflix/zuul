package com.netflix.zuul.sample.push;

import com.netflix.netty.common.channel.config.ChannelConfig;
import com.netflix.zuul.netty.server.ZuulDependencyKeys;
import com.netflix.zuul.netty.server.push.*;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.group.ChannelGroup;

/**
 * Author: Susheel Aroskar
 * Date: 6/8/18
 */
public class SampleSSEPushChannelInitializer extends PushChannelInitializer {

    private final PushConnectionRegistry pushConnectionRegistry;
    private final PushAuthHandler pushAuthHandler;

    public SampleSSEPushChannelInitializer(
            String metricId,
            ChannelConfig channelConfig,
            ChannelConfig channelDependencies,
            ChannelGroup channels) {
        super(metricId, channelConfig, channelDependencies, channels);
        pushConnectionRegistry = channelDependencies.get(ZuulDependencyKeys.pushConnectionRegistry);
        pushAuthHandler = new SamplePushAuthHandler(PushProtocol.SSE.getPath());
    }


    @Override
    protected void addPushHandlers(final ChannelPipeline pipeline) {
        pipeline.addLast(PushAuthHandler.NAME, pushAuthHandler);
        pipeline.addLast(new PushRegistrationHandler(pushConnectionRegistry, PushProtocol.SSE));
        pipeline.addLast(new SampleSSEPushClientProtocolHandler());
    }

}
