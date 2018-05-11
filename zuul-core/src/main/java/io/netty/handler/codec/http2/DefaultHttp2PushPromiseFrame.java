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

import static io.netty.handler.codec.http2.Http2CodecUtil.verifyPadding;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

public class DefaultHttp2PushPromiseFrame extends AbstractHttp2StreamFrame implements Http2PushPromiseFrame {
    private final Http2Headers headers;
    private final boolean endStream;
    private final int padding;
    private final int promisedStreamId;

    public DefaultHttp2PushPromiseFrame(Http2Headers headers, int promisedStreamId) {
        this(headers, false, promisedStreamId);
    }

    public DefaultHttp2PushPromiseFrame(Http2Headers headers, boolean endStream, int promisedStreamId) {
        this(headers, endStream, 0, promisedStreamId);
    }

    public DefaultHttp2PushPromiseFrame(Http2Headers headers, boolean endStream, int padding, int promisedStreamId) {
        this.headers = checkNotNull(headers, "headers");
        this.endStream = endStream;
        verifyPadding(padding);
        this.padding = padding;
        this.promisedStreamId = promisedStreamId;
    }

    @Override
    public DefaultHttp2PushPromiseFrame stream(Http2FrameStream stream) {
        super.stream(stream);
        return this;
    }

    @Override
    public String name() {
        return "PUSH_PROMISE";
    }

    @Override
    public Http2Headers headers() {
        return headers;
    }

    @Override
    public boolean isEndStream() {
        return endStream;
    }

    @Override
    public int padding() {
        return padding;
    }

    @Override
    public int promisedStreamId() {
        return promisedStreamId;
    }

    @Override
    public String toString() {
        return "DefaultHttp2PushPromiseFrame(streamId=" + stream().id() + ", headers=" + headers
                + ", endStream=" + endStream + ", padding=" + padding + ", promisedStreamId=" + promisedStreamId + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DefaultHttp2PushPromiseFrame)) {
            return false;
        }
        DefaultHttp2PushPromiseFrame other = (DefaultHttp2PushPromiseFrame) o;
        return super.equals(other) && headers.equals(other.headers)
                && endStream == other.endStream && padding == other.padding
                && promisedStreamId == other.promisedStreamId;
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = hash * 31 + headers.hashCode();
        hash = hash * 31 + (endStream ? 0 : 1);
        hash = hash * 31 + padding;
        hash = hash * 31 + promisedStreamId;
        return hash;
    }
}