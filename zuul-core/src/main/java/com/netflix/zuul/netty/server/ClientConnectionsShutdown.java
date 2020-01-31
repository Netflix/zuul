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

package com.netflix.zuul.netty.server;

import com.netflix.netty.common.ConnectionCloseChannelAttributes;
import com.netflix.netty.common.ConnectionCloseType;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.channel.group.ChannelGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseCombiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * TODO: Change this class to be an instance per-port.
 * So that then the configuration can be different per-port, which is need for the combined FTL/Cloud clusters.
 *
 * User: michaels@netflix.com
 * Date: 3/6/17
 * Time: 12:36 PM
 */
public class ClientConnectionsShutdown
{
    private static final Logger LOG = LoggerFactory.getLogger(ClientConnectionsShutdown.class);

    private final ChannelGroup channels;
    private final EventExecutor executor;

    public ClientConnectionsShutdown(ChannelGroup channels, EventExecutor executor)
    {
        this.channels = channels;
        this.executor = executor;

        // Only uncomment this for local testing!
        // Allow a fast property to invoke connection shutdown for testing purposes.
//        DynamicBooleanProperty DEBUG_SHUTDOWN = new DynamicBooleanProperty("server.outofservice.connections.shutdown.debug", false);
//        DEBUG_SHUTDOWN.addCallback(() -> {
//            if (DEBUG_SHUTDOWN.get()) {
//                gracefullyShutdownClientChannels();
//            }
//        });
    }

    /**
     * Note this blocks until all the channels have finished closing.
     */
    public void gracefullyShutdownClientChannels()
    {
        LOG.warn("Gracefully shutting down all client channels");
        try {


            // Mark all active connections to be closed after next response sent.
            LOG.warn("Flagging CLOSE_AFTER_RESPONSE on " + channels.size() + " client channels.");
            // Pick some arbitrary executor.
            PromiseCombiner closeAfterPromises = new PromiseCombiner(ImmediateEventExecutor.INSTANCE);
            for (Channel channel : channels)
            {
                ConnectionCloseType.setForChannel(channel, ConnectionCloseType.DELAYED_GRACEFUL);

                ChannelPromise closePromise = channel.pipeline().newPromise();
                channel.attr(ConnectionCloseChannelAttributes.CLOSE_AFTER_RESPONSE).set(closePromise);
                // TODO(carl-mastrangelo): remove closePromise, since I don't think it's needed.  Need to verify.
                closeAfterPromises.add(channel.closeFuture());
            }

            // Wait for all of the attempts to close connections gracefully, or max of 30 secs each.
            Promise<Void> combinedCloseAfterPromise = executor.newPromise();
            closeAfterPromises.finish(combinedCloseAfterPromise);
            combinedCloseAfterPromise.await(30, TimeUnit.SECONDS);

            // Close all of the remaining active connections.
            LOG.warn("Closing remaining active client channels.");
            List<ChannelFuture> forceCloseFutures = new ArrayList<>();
            channels.forEach(channel -> {
                if (channel.isActive()) {
                    ChannelFuture f = channel.pipeline().close();
                    forceCloseFutures.add(f);
                }
            });

            LOG.warn("Waiting for " + forceCloseFutures.size() + " client channels to be closed.");
            PromiseCombiner closePromisesCombiner = new PromiseCombiner(ImmediateEventExecutor.INSTANCE);
            closePromisesCombiner.addAll(forceCloseFutures.toArray(new ChannelFuture[0]));
            Promise<Void> combinedClosePromise = executor.newPromise();
            closePromisesCombiner.finish(combinedClosePromise);
            combinedClosePromise.await(5, TimeUnit.SECONDS);
            LOG.warn(forceCloseFutures.size() + " client channels closed.");
        }
        catch (InterruptedException ie) {
            LOG.warn("Interrupted while shutting down client channels");
        }
    }
}
