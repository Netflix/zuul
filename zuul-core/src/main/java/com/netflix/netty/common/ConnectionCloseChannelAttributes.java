/*
 * Copyright 2019 Netflix, Inc.
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
