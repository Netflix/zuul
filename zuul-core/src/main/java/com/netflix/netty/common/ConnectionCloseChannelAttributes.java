package com.netflix.netty.common;

import com.netflix.netty.common.channel.config.ChannelConfig;
import com.netflix.netty.common.channel.config.CommonChannelConfigKeys;
import com.netflix.zuul.netty.server.BaseZuulChannelInitializer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.util.AttributeKey;

public class ConnectionCloseChannelAttributes
{
    public static final AttributeKey<ChannelPromise> CLOSE_AFTER_RESPONSE = AttributeKey.newInstance("CLOSE_AFTER_RESPONSE");
    public static final AttributeKey<ConnectionCloseType> CLOSE_TYPE = AttributeKey.newInstance("CLOSE_TYPE");

    public static int gracefulCloseDelay(Channel channel)
    {
        ChannelConfig channelConfig = channel.attr(BaseZuulChannelInitializer.ATTR_CHANNEL_CONFIG).get();
        Integer gracefulCloseDelay = channelConfig.get(CommonChannelConfigKeys.connCloseDelay);
        return gracefulCloseDelay == null ? 0 : gracefulCloseDelay.intValue();
    }

    public static boolean allowGracefulDelayed(Channel channel)
    {
        ChannelConfig channelConfig = channel.attr(BaseZuulChannelInitializer.ATTR_CHANNEL_CONFIG).get();
        Boolean value = channelConfig.get(CommonChannelConfigKeys.http2AllowGracefulDelayed);
        return value == null ? false : value.booleanValue();
    }
}
