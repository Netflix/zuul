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
package io.netty.handler.codec.http2;

import com.netflix.config.DynamicStringSetProperty;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.logging.LogLevel;
import io.netty.util.internal.UnstableApi;
import io.netty.util.internal.logging.InternalLogLevel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Logs HTTP2 frames for debugging purposes.
 */
@UnstableApi
public class Http2FrameLogger extends ChannelHandlerAdapter {

    public enum Direction {
        INBOUND,
        OUTBOUND
    }

    private static final DynamicStringSetProperty FRAMES_TO_LOG = new DynamicStringSetProperty("server.http2.logger.framestolog",
            "SETTINGS,WINDOW_UPDATE,HEADERS,GO_AWAY,RST_STREAM,PRIORITY,PING,PUSH_PROMISE");

    private static final int BUFFER_LENGTH_THRESHOLD = 64;
    private final InternalLogger logger;
    private final InternalLogLevel level;

    public Http2FrameLogger(LogLevel level) {
        this(level.toInternalLevel(), InternalLoggerFactory.getInstance(io.netty.handler.codec.http2.Http2FrameLogger.class));
    }

    public Http2FrameLogger(LogLevel level, String name) {
        this(level.toInternalLevel(), InternalLoggerFactory.getInstance(name));
    }

    public Http2FrameLogger(LogLevel level, Class<?> clazz) {
        this(level.toInternalLevel(), InternalLoggerFactory.getInstance(clazz));
    }

    private Http2FrameLogger(InternalLogLevel level, InternalLogger logger) {
        this.level = checkNotNull(level, "level");
        this.logger = checkNotNull(logger, "logger");
    }

    public void logData(Direction direction, ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
                        boolean endStream) {
        if (enabled()) {
            log(direction, "DATA", ctx,
                    "streamId=%d, endStream=%b, length=%d",
                    streamId, endStream, data.readableBytes());
        }
    }

    public void logHeaders(Direction direction, ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                           int padding, boolean endStream) {
        if (enabled()) {
            log(direction, "HEADERS", ctx, "streamId=%d, headers=%s, endStream=%b",
                    streamId, headers, endStream);
        }
    }

    public void logHeaders(Direction direction, ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                           int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) {
        if (enabled()) {
            log(direction, "HEADERS", ctx,
                    "streamId=%d, headers=%s, streamDependency=%d, weight=%d, "
                            + "exclusive=%b, endStream=%b",
                    streamId, headers, streamDependency, weight, exclusive, endStream);
        }
    }

    public void logPriority(Direction direction, ChannelHandlerContext ctx, int streamId, int streamDependency,
                            short weight, boolean exclusive) {
        if (enabled()) {
            log(direction, "PRIORITY", ctx, "streamId=%d, streamDependency=%d, weight=%d, exclusive=%b",
                    streamId, streamDependency, weight, exclusive);
        }
    }

    public void logRstStream(Direction direction, ChannelHandlerContext ctx, int streamId, long errorCode) {
        if (enabled()) {
            log(direction, "RST_STREAM", ctx, "streamId=%d, errorCode=%d", streamId, errorCode);
        }
    }

    public void logSettingsAck(Direction direction, ChannelHandlerContext ctx) {
        if (enabled()) {
            log(direction, "SETTINGS", ctx, "ack=true");
        }
    }

    public void logSettings(Direction direction, ChannelHandlerContext ctx, Http2Settings settings) {
        if (enabled()) {
            log(direction, "SETTINGS", ctx, "ack=false, settings=%s", settings);
        }
    }

    public void logPing(Direction direction, ChannelHandlerContext ctx, ByteBuf data) {
        if (enabled()) {
            log(direction, "PING", ctx, "ack=false, length=%d, bytes=%s",
                    data.readableBytes(), toString(data));
        }
    }

    public void logPingAck(Direction direction, ChannelHandlerContext ctx, ByteBuf data) {
        if (enabled()) {
            log(direction, "PING", ctx, "ack=true, length=%d, bytes=%s",
                    data.readableBytes(), toString(data));
        }
    }

    public void logPushPromise(Direction direction, ChannelHandlerContext ctx, int streamId, int promisedStreamId,
                               Http2Headers headers, int padding) {
        if (enabled()) {
            log(direction, "PUSH_PROMISE", ctx, "streamId=%d, promisedStreamId=%d, headers=%s, padding=%d",
                    streamId, promisedStreamId, headers, padding);
        }
    }

    public void logGoAway(Direction direction, ChannelHandlerContext ctx, int lastStreamId, long errorCode,
                          ByteBuf debugData) {
        if (enabled()) {
            log(direction, "GO_AWAY", ctx, "lastStreamId=%d, errorCode=%d, length=%d, bytes=%s",
                    lastStreamId, errorCode, debugData.readableBytes(), toString(debugData));
        }
    }

    public void logWindowsUpdate(Direction direction, ChannelHandlerContext ctx, int streamId,
                                 int windowSizeIncrement) {
        if (enabled()) {
            log(direction, "WINDOW_UPDATE", ctx, "streamId=%d, windowSizeIncrement=%d",
                    streamId, windowSizeIncrement);
        }
    }

    public void logUnknownFrame(Direction direction, ChannelHandlerContext ctx, byte frameType, int streamId,
                                Http2Flags flags, ByteBuf data) {
        if (enabled()) {
            log(direction, "UNKNOWN", ctx, "frameType=%d, streamId=%d, flags=%d, length=%d, bytes=%s",
                    frameType & 0xFF, streamId, flags.value(), data.readableBytes(), toString(data));
        }
    }

    protected boolean enabled() {
        return logger.isEnabled(level);
    }

    private String toString(ByteBuf buf) {
        if (level == InternalLogLevel.TRACE || buf.readableBytes() <= BUFFER_LENGTH_THRESHOLD) {
            // Log the entire buffer.
            return ByteBufUtil.hexDump(buf);
        }

        // Otherwise just log the first 64 bytes.
        int length = Math.min(buf.readableBytes(), BUFFER_LENGTH_THRESHOLD);
        return ByteBufUtil.hexDump(buf, buf.readerIndex(), length) + "...";
    }

    private void log(Direction direction, String frame, ChannelHandlerContext ctx, String format, Object... args) {
        if (shouldLogFrame(frame)) {
            StringBuilder b = new StringBuilder(200)
                    .append(direction.name())
                    .append(": ")
                    .append(frame)
                    .append(": ")
                    .append(String.format(format, args))
                    .append(" -- ")
                    .append(String.valueOf(ctx.channel()));
            logger.log(level, b.toString());
        }
    }

    protected boolean shouldLogFrame(String frame) {
        return FRAMES_TO_LOG.get().contains(frame);
    }
}
