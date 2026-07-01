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

import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import org.jspecify.annotations.NullMarked;

/**
 * Stream level handler used for tracking request volume on http/2 streams. This handler should
 * be shared between streams on a common connection, but each connection requires a unique instance.
 * A {@link ConnectionCloseEvent} will be fired on the parent (i.e. connection) pipeline, and is expected that
 * a different handler, like {@link Http2ConnectionCloseHandler}, will handle the event.
 */
@NullMarked
@ChannelHandler.Sharable
public class Http2ConnectionExpiryHandler extends AbstractHttpConnectionExpiryHandler {
    public Http2ConnectionExpiryHandler(int maxRequests, int maxExpiry) {
        super(maxRequests, maxExpiry);
    }

    @Override
    protected boolean isResponse(Object msg) {
        // only count end frames to avoid double counting
        return (msg instanceof Http2HeadersFrame headersFrame && headersFrame.isEndStream())
                || (msg instanceof Http2DataFrame dataFrame && dataFrame.isEndStream());
    }
}
