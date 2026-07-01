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
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler that can be used to close connections after handling a configurable number of requests, or after a configurable
 * amount of time. It's important to note that expirations are driven by request processing, so this handler will not
 * close idle, long-lived connections until handling a response.
 *
 * This handler itself does not close channels, it's expected that a handler on the connection's channel handler pipeline
 * handles a {@link ConnectionCloseEvent} and closes the corresponding channel
 *
 * See also,
 * {@link Http1ConnectionCloseHandler}
 * {@link Http2ConnectionCloseHandler}
 */
@NullMarked
public abstract class AbstractHttpConnectionExpiryHandler extends ChannelOutboundHandlerAdapter {

    private static final ConnectionCloseEvent.Graceful EXPIRATION_EVENT =
            new ConnectionCloseEvent.Graceful(CloseReason.EXPIRATION);

    static final Logger LOG = LoggerFactory.getLogger(AbstractHttpConnectionExpiryHandler.class);
    static final CachedDynamicLongProperty MAX_EXPIRY_DELTA =
            new CachedDynamicLongProperty("server.connection.expiry.delta", 20 * 1000);

    protected final int maxRequests;
    protected final int maxExpiry;
    protected final long connectionStartTime;
    protected final long connectionExpiryTime;
    private final Clock clock;

    private int count = 0;

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
        if (isResponse(msg)) {
            count++;
            if (isConnectionExpired(ctx.channel())) {
                Channel channel = HttpUtils.getMainChannel(ctx);
                promise.addListener(f -> {
                    channel.pipeline().fireUserEventTriggered(EXPIRATION_EVENT);
                });
            }
        }

        ctx.write(msg, promise);
    }

    protected boolean isConnectionExpired(Channel channel) {
        long now = clock.millis();
        boolean expired = count >= maxRequests || now > connectionExpiryTime;
        if (expired) {
            if (LOG.isInfoEnabled()) {
                long lifetime = now - connectionStartTime;
                LOG.info(
                        "Connection is expired. requestCount={}, lifetime={}, {}",
                        count,
                        lifetime,
                        ChannelUtils.channelInfoForLogging(channel));
            }
        }
        return expired;
    }

    int getCount() {
        return count;
    }

    protected abstract boolean isResponse(Object msg);
}
