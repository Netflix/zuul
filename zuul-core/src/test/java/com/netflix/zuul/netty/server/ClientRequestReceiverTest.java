/*
 * Copyright 2019 Netflix, Inc.
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.netflix.netty.common.SourceAddressChannelHandler;
import com.netflix.zuul.message.http.HttpRequestMessageImpl;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link ClientRequestReceiver}.
 */
@RunWith(JUnit4.class)
public class ClientRequestReceiverTest {
    @Test
    public void largeResponse_atLimit() {
        ClientRequestReceiver receiver = new ClientRequestReceiver(null);
        EmbeddedChannel channel = new EmbeddedChannel(receiver);
        // Required for messages
        channel.attr(SourceAddressChannelHandler.ATTR_SERVER_LOCAL_PORT).set(1234);

        int maxSize;
        // Figure out the max size, since it isn't public.
        {
            ByteBuf buf = Unpooled.buffer(1).writeByte('a');
            channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/post", buf));
            HttpRequestMessageImpl res = channel.readInbound();
            maxSize = res.getMaxBodySize();
            res.disposeBufferedBody();
        }

        HttpRequestMessageImpl result;
        {
            ByteBuf buf = Unpooled.buffer(maxSize);
            buf.writerIndex(maxSize);
            channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/post", buf));
            result = channel.readInbound();
            result.disposeBufferedBody();
        }

        assertNull(result.getContext().getError());
        assertFalse(result.getContext().shouldSendErrorResponse());
        channel.close();
    }

    @Test
    public void largeResponse_aboveLimit() {
        ClientRequestReceiver receiver = new ClientRequestReceiver(null);
        EmbeddedChannel channel = new EmbeddedChannel(receiver);
        // Required for messages
        channel.attr(SourceAddressChannelHandler.ATTR_SERVER_LOCAL_PORT).set(1234);

        int maxSize;
        // Figure out the max size, since it isn't public.
        {
            ByteBuf buf = Unpooled.buffer(1).writeByte('a');
            channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/post", buf));
            HttpRequestMessageImpl res = channel.readInbound();
            maxSize = res.getMaxBodySize();
            res.disposeBufferedBody();
        }

        HttpRequestMessageImpl result;
        {
            ByteBuf buf = Unpooled.buffer(maxSize + 1);
            buf.writerIndex(maxSize + 1);
            channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/post", buf));
            result = channel.readInbound();
            result.disposeBufferedBody();
        }

        assertNotNull(result.getContext().getError());
        assertTrue(result.getContext().getError().getMessage().contains("too large"));
        assertTrue(result.getContext().shouldSendErrorResponse());
        channel.close();
    }
}
