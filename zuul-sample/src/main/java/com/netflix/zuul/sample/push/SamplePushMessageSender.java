package com.netflix.zuul.sample.push;

import com.google.common.base.Strings;
import com.google.inject.Singleton;
import com.netflix.zuul.netty.server.push.PushConnectionRegistry;
import com.netflix.zuul.netty.server.push.PushMessageSender;
import com.netflix.zuul.netty.server.push.PushUserAuth;
import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Author: Susheel Aroskar
 * Date: 5/16/18
 */
@Singleton
@ChannelHandler.Sharable
public class SamplePushMessageSender extends PushMessageSender {

    public SamplePushMessageSender(PushConnectionRegistry pushConnectionRegistry) {
        super(pushConnectionRegistry);
    }

    @Override
    protected PushUserAuth getPushUserAuth(FullHttpRequest request) {
        final String cid = request.headers().get("X-CUSTOMER_ID");
        if (Strings.isNullOrEmpty(cid)) {
            return new SamplePushUserAuth(HttpResponseStatus.UNAUTHORIZED.code());
        }
        return new SamplePushUserAuth(cid);

    }

}

