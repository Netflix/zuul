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

/**
 * Stream level handler used for tracking expiration on the parent connection. This handler is safe to share
 * between streams, but each channel must have a unique instance
 *
 * This needs to be inserted in the stream pipeline before any h2->h1 conversion.
 *
 * User: michaels@netflix.com
 * Date: 2/8/17
 * Time: 9:58 AM
 */
@ChannelHandler.Sharable
public class Http2ConnectionExpiryHandler extends AbstractHttpConnectionExpiryHandler {
    public Http2ConnectionExpiryHandler(int maxRequests, int maxExpiry) {
        super(maxRequests, maxExpiry);
    }

    @Override
    protected boolean isTerminalResponse(Object msg) {
        return (msg instanceof Http2HeadersFrame headersFrame && headersFrame.isEndStream())
                || (msg instanceof Http2DataFrame dataFrame && dataFrame.isEndStream());
    }
}
