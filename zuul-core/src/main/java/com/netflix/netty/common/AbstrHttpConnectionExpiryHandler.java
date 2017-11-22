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

package com.netflix.netty.common;

import com.netflix.config.CachedDynamicLongProperty;
import com.netflix.zuul.netty.ChannelUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

/**
 * User: michaels@netflix.com
 * Date: 7/17/17
 * Time: 10:54 AM
 */
public abstract class AbstrHttpConnectionExpiryHandler extends ChannelOutboundHandlerAdapter
{
    protected final static Logger LOG = LoggerFactory.getLogger(AbstrHttpConnectionExpiryHandler.class);
    protected final static CachedDynamicLongProperty MAX_EXPIRY_DELTA = new CachedDynamicLongProperty(
            "server.connection.expiry.delta", 20 * 1000);

    protected final ConnectionCloseType connectionCloseType;
    protected final int maxRequests;
    protected final int maxExpiry;
    protected final long connectionStartTime;
    protected final long connectionExpiryTime;

    protected int requestCount = 0;
    protected int maxRequestsUnderBrownout = 0;

    public AbstrHttpConnectionExpiryHandler(ConnectionCloseType connectionCloseType, int maxRequestsUnderBrownout, int maxRequests, int maxExpiry)
    {
        this.connectionCloseType = connectionCloseType;
        this.maxRequestsUnderBrownout = maxRequestsUnderBrownout;
        this.maxRequests = maxRequests;

        this.maxExpiry = maxExpiry;
        this.connectionStartTime = System.currentTimeMillis();
        long randomDelta = ThreadLocalRandom.current().nextLong(MAX_EXPIRY_DELTA.get());
        this.connectionExpiryTime = connectionStartTime + maxExpiry + randomDelta;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception
    {
        if (isResponseHeaders(msg)) {
            // Update the request count attribute for this channel.
            requestCount++;

            if (isConnectionExpired(ctx)) {
                // Flag this channel to be closed after response is written.
                HttpChannelFlags.CLOSE_AFTER_RESPONSE.set(ctx);
                // with close of configured type.
                ConnectionCloseType.setForChannel(ctx.channel(), connectionCloseType);
            }
        }

        super.write(ctx, msg, promise);
    }

    protected boolean isConnectionExpired(ChannelHandlerContext ctx)
    {
        boolean expired = requestCount >= maxRequests(ctx.channel()) ||
                System.currentTimeMillis() > connectionExpiryTime;
        if (expired) {
            long lifetime = System.currentTimeMillis() - connectionStartTime;
            LOG.info("Connection is expired. requestCount={}, lifetime={}, {}",
                    requestCount, lifetime, ChannelUtils.channelInfoForLogging(ctx.channel()));
        }
        return expired;
    }

    protected abstract boolean isResponseHeaders(Object msg);

    protected int maxRequests(Channel ch)
    {
        if (HttpChannelFlags.IN_BROWNOUT.get(ch)) {
            return this.maxRequestsUnderBrownout;
        }
        else {
            return this.maxRequests;
        }
    }
}
