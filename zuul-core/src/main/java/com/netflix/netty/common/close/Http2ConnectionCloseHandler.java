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

import com.netflix.spectator.api.Registry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 *
 *
 * User: michaels@netflix.com
 * Date: 2/8/17
 * Time: 2:03 PM
 */
public class Http2ConnectionCloseHandler extends BaseConnectionCloseHandler {

    private ScheduledFuture<?> scheduledClose;

    public Http2ConnectionCloseHandler(Registry registry) {
        super(registry);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof ConnectionCloseEvent event && !isFlaggedForClose()) {
            closeEvent = event;
            int port = getPort(ctx.channel());
            switch (event) {
                case ConnectionCloseEvent.Graceful ignored -> {
                    count(event, port);
                    ctx.close();
                }
                case ConnectionCloseEvent.GracefulDelayed delayed -> {
                    long jitter = ThreadLocalRandom.current()
                            .nextLong(delayed.maxJitter().toMillis());
                    scheduledClose = ctx.executor()
                            .schedule(
                                    () -> {
                                        count(event, port);
                                        ctx.close();
                                    },
                                    jitter,
                                    TimeUnit.MILLISECONDS);
                }
            }
        }

        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (scheduledClose != null) {
            scheduledClose.cancel(false);
            scheduledClose = null;
        }
        ctx.fireChannelInactive();
    }

    @Override
    protected String getProtocol() {
        return "http/2";
    }
}
