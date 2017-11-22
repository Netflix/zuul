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

import com.netflix.netty.common.http2.DynamicHttp2FrameLogger;

import static io.netty.handler.logging.LogLevel.INFO;

/**
 * This is a hopefully temporary hack to override the netty Http2FrameCodec class just to allow
 * us to pass the Http2Settings initialSettings parameter to the constructor.
 *
 * As of netty v4.1.8 the underlying constructor on Http2FrameCodec is package protected.
 *
 * User: michaels@netflix.com
 * Date: 2/6/17
 * Time: 12:12 PM
 */
public class Http2FrameCodecWithInitialSettings extends Http2FrameCodec
{
    private static final Http2FrameLogger HTTP2_FRAME_LOGGER = new DynamicHttp2FrameLogger(INFO, Http2FrameCodec.class);

    public Http2FrameCodecWithInitialSettings(boolean server, Http2Settings initialSettings)
    {
        this(server, new DefaultHttp2FrameWriter(), initialSettings);
    }

    public Http2FrameCodecWithInitialSettings(boolean server, Http2FrameWriter frameWriter, Http2Settings initialSettings)
    {
        super(server, frameWriter, HTTP2_FRAME_LOGGER, initialSettings);
    }
}
