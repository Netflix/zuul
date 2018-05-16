package com.netflix.zuul.sample.push;

import com.netflix.netty.common.channel.config.ChannelConfig;
import com.netflix.zuul.netty.server.ZuulDependencyKeys;
import com.netflix.zuul.netty.server.push.*;
import io.netty.channel.group.ChannelGroup;

/**
 * Author: Susheel Aroskar
 * Date: 5/16/18
 */
public class SamplePushChannelInitializer extends PushChannelInitializer {

    private final PushConnectionRegistry pushConnectionRegistry;
    private final PushAuthHandler pushAuthHandler;

    public SamplePushChannelInitializer(int port, ChannelConfig channelConfig, ChannelConfig channelDependencies, ChannelGroup channels) {
        super(port, channelConfig, channelDependencies, channels);
        pushConnectionRegistry = channelDependencies.get(ZuulDependencyKeys.pushConnectionRegistry);
        pushAuthHandler = new SamplePushAuthHandler();
    }


    @Override
    protected PushAuthHandler getPushAuthHandler() {
        return pushAuthHandler;
    }

    @Override
    protected PushRegistrationHandler getPushRegistrationHandler() {
        return new SamplePushRegistrationHandler(pushConnectionRegistry, PushProtocol.WEBSOCKET);
    }

}
