package com.netflix.zuul.sample.push;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.zuul.netty.server.push.PushConnectionRegistry;
import com.netflix.zuul.netty.server.push.PushMessageSender;
import com.netflix.zuul.netty.server.push.PushMessageSenderInitializer;

/**
 * Author: Susheel Aroskar
 * Date: 5/16/18
 */
@Singleton
public class SamplePushMessageSenderInitializer extends PushMessageSenderInitializer {

    private final PushMessageSender pushMessageSender;

    @Inject
    public SamplePushMessageSenderInitializer(PushConnectionRegistry pushConnectionRegistry) {
        super(pushConnectionRegistry);
        pushMessageSender = new SamplePushMessageSender(pushConnectionRegistry);
    }

    @Override
    protected PushMessageSender getPushMessageSender(PushConnectionRegistry pushConnectionRegistry) {
        return pushMessageSender;
    }

}
