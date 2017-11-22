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

package com.netflix.netty.common.metrics;

import com.netflix.spectator.api.Registry;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Frame;
import io.netty.handler.codec.http2.Http2GoAwayFrame;
import io.netty.handler.codec.http2.Http2ResetFrame;

public class Http2MetricsChannelHandlers
{
    private final Inbound inbound;
    private final Outbound outbound;
    
    public Http2MetricsChannelHandlers(Registry registry, String metricPrefix, String metricId)
    {
        super();
        this.inbound = new Inbound(registry, metricId, metricPrefix);
        this.outbound = new Outbound(registry, metricId, metricPrefix);
    }

    public Inbound inbound()
    {
        return inbound;
    }

    public Outbound outbound()
    {
        return outbound;
    }

    protected static void incrementErrorCounter(Registry registry, String counterName, String metricId, Http2Exception h2e)
    {
        String h2Error = h2e.error() != null ? h2e.error().name() : "NA";
        String exceptionName = h2e.getClass().getSimpleName();

        registry.counter(counterName,
                    "id", metricId,
                    "error", h2Error,
                    "exception", exceptionName)
                .increment();
    }

    protected static void incrementCounter(Registry registry, String counterName, String metricId, Http2Frame frame)
    {
        long errorCode;
        if (frame instanceof Http2ResetFrame) {
            errorCode = ((Http2ResetFrame) frame).errorCode();
        }
        else if (frame instanceof Http2GoAwayFrame) {
            errorCode = ((Http2GoAwayFrame) frame).errorCode();
        }
        else {
            errorCode = -1;
        }

        registry.counter(counterName,
                "id", metricId,
                "frame", frame.name(),
                "error_code", Long.toString(errorCode))
                .increment();
    }

    @ChannelHandler.Sharable
    private static class Inbound extends ChannelInboundHandlerAdapter
    {
        private final Registry registry;
        private final String metricId;
        private final String frameCounterName;
        private final String errorCounterName;

        public Inbound(Registry registry, String metricId, String metricPrefix)
        {
            this.registry = registry;
            this.metricId = metricId;
            this.frameCounterName = metricPrefix + ".http2.frame.inbound";
            this.errorCounterName = metricPrefix + ".http2.error.inbound";
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception
        {
            try {
                if (msg instanceof Http2Frame) {
                    incrementCounter(registry, frameCounterName, metricId, (Http2Frame) msg);
                }
            }
            finally {
                super.channelRead(ctx, msg);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception
        {
            try {
                if (cause instanceof Http2Exception) {
                    incrementErrorCounter(registry, errorCounterName, metricId, (Http2Exception) cause);
                }
            }
            finally {
                super.exceptionCaught(ctx, cause);
            }
        }
    }

    @ChannelHandler.Sharable
    private static class Outbound extends ChannelOutboundHandlerAdapter
    {
        private final Registry registry;
        private final String metricId;
        private final String frameCounterName;
        private final String errorCounterName;

        public Outbound(Registry registry, String metricId, String metricPrefix)
        {
            this.registry = registry;
            this.metricId = metricId;
            this.frameCounterName = metricPrefix + ".http2.frame.outbound";
            this.errorCounterName = metricPrefix + ".http2.error.outbound";
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception
        {
            super.write(ctx, msg, promise);

            if (msg instanceof Http2Frame) {
                incrementCounter(registry, frameCounterName, metricId, (Http2Frame) msg);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception
        {
            try {
                if (cause instanceof Http2Exception) {
                    incrementErrorCounter(registry, errorCounterName, metricId, (Http2Exception) cause);
                }
            }
            finally {
                super.exceptionCaught(ctx, cause);
            }
        }
    }
}
