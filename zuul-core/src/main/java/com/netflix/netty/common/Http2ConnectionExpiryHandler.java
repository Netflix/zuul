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

import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.http2.Http2HeadersFrame;

/**
 * This needs to be inserted in the pipeline after the Http2 Codex, but before any h2->h1 conversion.
 *
 * User: michaels@netflix.com
 * Date: 2/8/17
 * Time: 9:58 AM
 */
@ChannelHandler.Sharable
public class Http2ConnectionExpiryHandler extends AbstrHttpConnectionExpiryHandler
{
    public Http2ConnectionExpiryHandler(int maxRequests, int maxRequestsUnderBrownout, int maxExpiry)
    {
        super(ConnectionCloseType.DELAYED_GRACEFUL, maxRequestsUnderBrownout, maxRequests, maxExpiry);
    }

    @Override
    protected boolean isResponseHeaders(Object msg)
    {
        return msg instanceof Http2HeadersFrame;
    }
}
