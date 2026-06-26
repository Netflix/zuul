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

package com.netflix.netty.common.close;

import com.google.common.base.Preconditions;
import com.netflix.config.CachedDynamicLongProperty;
import com.netflix.zuul.netty.ChannelUtils;
import com.netflix.zuul.util.HttpUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import java.time.Clock;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User: michaels@netflix.com
 * Date: 7/17/17
 * Time: 10:54 AM
 */
public abstract class AbstractHttpConnectionExpiryHandler extends ChannelOutboundHandlerAdapter {
    protected static final Logger LOG = LoggerFactory.getLogger(AbstractHttpConnectionExpiryHandler.class);
    protected static final CachedDynamicLongProperty MAX_EXPIRY_DELTA =
            new CachedDynamicLongProperty("server.connection.expiry.delta", 20 * 1000);

    protected final int maxRequests;
    protected final int maxExpiry;
    protected final long connectionStartTime;
    protected final long connectionExpiryTime;

    protected int requestCount = 0;

    private final Clock clock;

    public AbstractHttpConnectionExpiryHandler(int maxRequests, int maxExpiry) {
        this(maxRequests, maxExpiry, Clock.systemUTC());
    }

    AbstractHttpConnectionExpiryHandler(int maxRequests, int maxExpiry, Clock clock) {
        Preconditions.checkArgument(maxExpiry > 0);
        Preconditions.checkArgument(maxRequests > 0);

        this.clock = clock;
        this.maxRequests = maxRequests;
        this.maxExpiry = maxExpiry;
        this.connectionStartTime = clock.millis();
        long randomDelta = ThreadLocalRandom.current().nextLong(MAX_EXPIRY_DELTA.get());
        this.connectionExpiryTime = connectionStartTime + maxExpiry + randomDelta;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (isTerminalResponse(msg)) {
            // Update the request count attribute for this channel.
            requestCount++;

            if (isConnectionExpired(ctx.channel())) {
                Channel channel = HttpUtils.getMainChannel(ctx);
                promise.addListener(f -> {
                    channel.pipeline()
                            .fireUserEventTriggered(new ConnectionCloseEvent.Graceful(CloseReason.EXPIRATION));
                });
            }
        }

        ctx.write(msg, promise);
    }

    protected boolean isConnectionExpired(Channel channel) {
        long now = clock.millis();
        boolean expired = requestCount >= maxRequests || now > connectionExpiryTime;
        if (expired) {
            long lifetime = now - connectionStartTime;
            LOG.info(
                    "Connection is expired. requestCount={}, lifetime={}, {}",
                    requestCount,
                    lifetime,
                    ChannelUtils.channelInfoForLogging(channel));
        }
        return expired;
    }

    protected abstract boolean isTerminalResponse(Object msg);
}
