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

package com.netflix.netty.common.http2;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.logging.LogLevel;
import io.netty.util.AttributeKey;

public class DynamicHttp2FrameLogger extends Http2FrameLogger
{
    public static final AttributeKey<Object> ATTR_ENABLE = AttributeKey.valueOf("http2.frame.logger.enabled");
    
    public DynamicHttp2FrameLogger(LogLevel level, Class<?> clazz)
    {
        super(level, clazz);
    }

    protected boolean enabled(ChannelHandlerContext ctx)
    {
        return ctx.channel().hasAttr(ATTR_ENABLE);
    }

    @Override
    protected boolean enabled()
    {
        // Always return true, as our real enable check is per channel.
        return true;
    }

    @Override
    public void logData(Direction direction, ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endStream)
    {
        if (enabled(ctx))
            super.logData(direction, ctx, streamId, data, padding, endStream);
    }

    @Override
    public void logHeaders(Direction direction, ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding, boolean endStream)
    {
        if (enabled(ctx))
            super.logHeaders(direction, ctx, streamId, headers, padding, endStream);
    }

    @Override
    public void logHeaders(Direction direction, ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream)
    {
        if (enabled(ctx))
            super.logHeaders(direction, ctx, streamId, headers, streamDependency, weight, exclusive, padding, endStream);
    }

    @Override
    public void logPriority(Direction direction, ChannelHandlerContext ctx, int streamId, int streamDependency, short weight, boolean exclusive)
    {
        if (enabled(ctx))
            super.logPriority(direction, ctx, streamId, streamDependency, weight, exclusive);
    }

    @Override
    public void logRstStream(Direction direction, ChannelHandlerContext ctx, int streamId, long errorCode)
    {
        if (enabled(ctx))
            super.logRstStream(direction, ctx, streamId, errorCode);
    }

    @Override
    public void logSettingsAck(Direction direction, ChannelHandlerContext ctx)
    {
        if (enabled(ctx))
            super.logSettingsAck(direction, ctx);
    }

    @Override
    public void logSettings(Direction direction, ChannelHandlerContext ctx, Http2Settings settings)
    {
        if (enabled(ctx))
            super.logSettings(direction, ctx, settings);
    }

    @Override
    public void logPing(Direction direction, ChannelHandlerContext ctx, ByteBuf data)
    {
        if (enabled(ctx))
            super.logPing(direction, ctx, data);
    }

    @Override
    public void logPingAck(Direction direction, ChannelHandlerContext ctx, ByteBuf data)
    {
        if (enabled(ctx))
            super.logPingAck(direction, ctx, data);
    }

    @Override
    public void logPushPromise(Direction direction, ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers, int padding)
    {
        if (enabled(ctx))
            super.logPushPromise(direction, ctx, streamId, promisedStreamId, headers, padding);
    }

    @Override
    public void logGoAway(Direction direction, ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData)
    {
        if (enabled(ctx))
            super.logGoAway(direction, ctx, lastStreamId, errorCode, debugData);
    }

    @Override
    public void logWindowsUpdate(Direction direction, ChannelHandlerContext ctx, int streamId, int windowSizeIncrement)
    {
        if (enabled(ctx))
            super.logWindowsUpdate(direction, ctx, streamId, windowSizeIncrement);
    }

    @Override
    public void logUnknownFrame(Direction direction, ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags, ByteBuf data)
    {
        if (enabled(ctx))
            super.logUnknownFrame(direction, ctx, frameType, streamId, flags, data);
    }
}
