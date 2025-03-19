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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.net.InetAddresses;
import com.netflix.netty.common.HttpLifecycleChannelHandler;
import com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteEvent;
import com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteReason;
import com.netflix.netty.common.SourceAddressChannelHandler;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.zuul.context.CommonContextKeys;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpRequestMessageImpl;
import com.netflix.zuul.netty.insights.PassportLoggingHandler;
import com.netflix.zuul.stats.status.StatusCategoryUtils;
import com.netflix.zuul.stats.status.ZuulStatusCategory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ClientRequestReceiver}.
 */
@ExtendWith(MockitoExtension.class)
class ClientRequestReceiverTest {

    @Test
    void proxyProtocol_portSetInSessionContextAndInHttpRequestMessageImpl() {
        EmbeddedChannel channel = new EmbeddedChannel(new ClientRequestReceiver(null));
        channel.attr(SourceAddressChannelHandler.ATTR_SERVER_LOCAL_PORT).set(1234);
        InetSocketAddress hapmDestinationAddress = new InetSocketAddress(InetAddresses.forString("2.2.2.2"), 444);
        channel.attr(SourceAddressChannelHandler.ATTR_PROXY_PROTOCOL_DESTINATION_ADDRESS)
                .set(hapmDestinationAddress);
        channel.attr(SourceAddressChannelHandler.ATTR_LOCAL_ADDR).set(hapmDestinationAddress);
        HttpRequestMessageImpl result;
        {
            channel.writeInbound(
                    new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/post", Unpooled.buffer()));
            result = channel.readInbound();
            result.disposeBufferedBody();
        }
        assertEquals((int) result.getClientDestinationPort().get(), hapmDestinationAddress.getPort());
        int destinationPort = ((InetSocketAddress)
                        result.getContext().get(CommonContextKeys.PROXY_PROTOCOL_DESTINATION_ADDRESS))
                .getPort();
        assertEquals(444, destinationPort);
        assertEquals(444, result.getOriginalPort());
        channel.close();
    }

    @Test
    void parseUriFromNetty_relative() {

        EmbeddedChannel channel = new EmbeddedChannel(new ClientRequestReceiver(null));
        channel.attr(SourceAddressChannelHandler.ATTR_SERVER_LOCAL_PORT).set(1234);
        HttpRequestMessageImpl result;
        {
            channel.writeInbound(new DefaultFullHttpRequest(
                    HttpVersion.HTTP_1_1,
                    HttpMethod.POST,
                    "/foo/bar/somePath/%5E1.0.0?param1=foo&param2=bar&param3=baz",
                    Unpooled.buffer()));
            result = channel.readInbound();
            result.disposeBufferedBody();
        }

        assertEquals("/foo/bar/somePath/%5E1.0.0", result.getPath());

        channel.close();
    }

    @Test
    void parseUriFromNetty_absolute() {

        EmbeddedChannel channel = new EmbeddedChannel(new ClientRequestReceiver(null));
        channel.attr(SourceAddressChannelHandler.ATTR_SERVER_LOCAL_PORT).set(1234);
        HttpRequestMessageImpl result;
        {
            channel.writeInbound(new DefaultFullHttpRequest(
                    HttpVersion.HTTP_1_1,
                    HttpMethod.POST,
                    "https://www.netflix.com/foo/bar/somePath/%5E1.0.0?param1=foo&param2=bar&param3=baz",
                    Unpooled.buffer()));
            result = channel.readInbound();
            result.disposeBufferedBody();
        }

        assertEquals("/foo/bar/somePath/%5E1.0.0", result.getPath());

        channel.close();
    }

    @Test
    void parseUriFromNetty_unknown() {

        EmbeddedChannel channel = new EmbeddedChannel(new ClientRequestReceiver(null));
        channel.attr(SourceAddressChannelHandler.ATTR_SERVER_LOCAL_PORT).set(1234);
        HttpRequestMessageImpl result;
        {
            channel.writeInbound(
                    new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "asdf", Unpooled.buffer()));
            result = channel.readInbound();
            result.disposeBufferedBody();
        }

        assertEquals("asdf", result.getPath());

        channel.close();
    }

    @Test
    void parseQueryParamsWithEncodedCharsInURI() {

        EmbeddedChannel channel = new EmbeddedChannel(new ClientRequestReceiver(null));
        channel.attr(SourceAddressChannelHandler.ATTR_SERVER_LOCAL_PORT).set(1234);
        HttpRequestMessageImpl result;
        {
            channel.writeInbound(new DefaultFullHttpRequest(
                    HttpVersion.HTTP_1_1,
                    HttpMethod.POST,
                    "/foo/bar/somePath/%5E1.0.0?param1=foo&param2=bar&param3=baz",
                    Unpooled.buffer()));
            result = channel.readInbound();
            result.disposeBufferedBody();
        }

        assertEquals("foo", result.getQueryParams().getFirst("param1"));
        assertEquals("bar", result.getQueryParams().getFirst("param2"));
        assertEquals("baz", result.getQueryParams().getFirst("param3"));

        channel.close();
    }

    @Test
    void largeResponse_atLimit() {
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
    void largeResponse_aboveLimit() {
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
        assertEquals(ZuulStatusCategory.FAILURE_CLIENT_BAD_REQUEST, StatusCategoryUtils.getStatusCategory(result));
        assertTrue(StatusCategoryUtils.getStatusCategoryReason(result.getContext())
                .startsWith("Invalid request provided: Request body size "));
        channel.close();
    }

    @Test
    void maxHeaderSizeExceeded_setBadRequestStatus() {

        int maxInitialLineLength = BaseZuulChannelInitializer.MAX_INITIAL_LINE_LENGTH.get();
        int maxHeaderSize = 10;
        int maxChunkSize = BaseZuulChannelInitializer.MAX_CHUNK_SIZE.get();
        ClientRequestReceiver receiver = new ClientRequestReceiver(null);
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestEncoder());
        PassportLoggingHandler loggingHandler = new PassportLoggingHandler(new DefaultRegistry());

        // Required for messages
        channel.attr(SourceAddressChannelHandler.ATTR_SERVER_LOCAL_PORT).set(1234);
        channel.pipeline().addLast(new HttpServerCodec(maxInitialLineLength, maxHeaderSize, maxChunkSize, false));
        channel.pipeline().addLast(receiver);
        channel.pipeline().addLast(loggingHandler);

        String str = "test-header-value";
        ByteBuf buf = Unpooled.buffer(1);
        HttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/post", buf);
        for (int i = 0; i < 100; i++) {
            httpRequest.headers().add("test-header" + i, str);
        }

        channel.writeOutbound(httpRequest);
        ByteBuf byteBuf = channel.readOutbound();
        channel.writeInbound(byteBuf);
        channel.readInbound();
        channel.close();

        HttpRequestMessage request = ClientRequestReceiver.getRequestFromChannel(channel);
        assertEquals(
                ZuulStatusCategory.FAILURE_CLIENT_BAD_REQUEST,
                StatusCategoryUtils.getStatusCategory(request.getContext()));
        assertEquals(
                "Invalid request provided: Decode failure",
                StatusCategoryUtils.getStatusCategoryReason(request.getContext()));
    }

    @Test
    void multipleHostHeaders_setBadRequestStatus() {
        ClientRequestReceiver receiver = new ClientRequestReceiver(null);
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestEncoder());
        PassportLoggingHandler loggingHandler = new PassportLoggingHandler(new DefaultRegistry());

        // Required for messages
        channel.attr(SourceAddressChannelHandler.ATTR_SERVER_LOCAL_PORT).set(1234);
        channel.pipeline().addLast(new HttpServerCodec());
        channel.pipeline().addLast(receiver);
        channel.pipeline().addLast(loggingHandler);

        HttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/post");
        httpRequest.headers().add("Host", "foo.bar.com");
        httpRequest.headers().add("Host", "bar.foo.com");

        channel.writeOutbound(httpRequest);
        ByteBuf byteBuf = channel.readOutbound();
        channel.writeInbound(byteBuf);
        channel.readInbound();
        channel.close();

        HttpRequestMessage request = ClientRequestReceiver.getRequestFromChannel(channel);
        SessionContext context = request.getContext();
        assertEquals(ZuulStatusCategory.FAILURE_CLIENT_BAD_REQUEST, StatusCategoryUtils.getStatusCategory(context));
        assertEquals("Multiple Host headers", context.getError().getMessage());
        assertEquals(
                "Invalid request provided: Multiple Host headers",
                StatusCategoryUtils.getStatusCategoryReason(context));
    }

    @Test
    void setStatusCategoryForHttpPipelining() {

        EmbeddedChannel channel = new EmbeddedChannel(new ClientRequestReceiver(null));
        channel.attr(SourceAddressChannelHandler.ATTR_SERVER_LOCAL_PORT).set(1234);

        DefaultFullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.POST, "?ELhAWDLM1hwm8bhU0UT4", Unpooled.buffer());

        // Write the message and save a copy
        channel.writeInbound(request);
        HttpRequestMessage inboundRequest = ClientRequestReceiver.getRequestFromChannel(channel);

        // Set the attr to emulate pipelining rejection
        channel.attr(HttpLifecycleChannelHandler.ATTR_HTTP_PIPELINE_REJECT).set(Boolean.TRUE);

        // Fire completion event
        channel.pipeline()
                .fireUserEventTriggered(new CompleteEvent(
                        CompleteReason.PIPELINE_REJECT,
                        request,
                        new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST)));
        channel.close();

        assertEquals(
                ZuulStatusCategory.FAILURE_CLIENT_PIPELINE_REJECT,
                StatusCategoryUtils.getStatusCategory(inboundRequest.getContext()));
    }

    @Test
    void headersAllCopied() {
        ClientRequestReceiver receiver = new ClientRequestReceiver(null);
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestEncoder());
        PassportLoggingHandler loggingHandler = new PassportLoggingHandler(new DefaultRegistry());

        // Required for messages
        channel.attr(SourceAddressChannelHandler.ATTR_SERVER_LOCAL_PORT).set(1234);
        channel.pipeline().addLast(new HttpServerCodec());
        channel.pipeline().addLast(receiver);
        channel.pipeline().addLast(loggingHandler);

        HttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/post");
        httpRequest.headers().add("Header1", "Value1");
        httpRequest.headers().add("Header2", "Value2");
        httpRequest.headers().add("Duplicate", "Duplicate1");
        httpRequest.headers().add("Duplicate", "Duplicate2");

        channel.writeOutbound(httpRequest);
        ByteBuf byteBuf = channel.readOutbound();
        channel.writeInbound(byteBuf);
        channel.readInbound();
        channel.close();

        HttpRequestMessage request = ClientRequestReceiver.getRequestFromChannel(channel);
        Headers headers = request.getHeaders();
        assertEquals(4, headers.size());
        assertEquals("Value1", headers.getFirst("Header1"));
        assertEquals("Value2", headers.getFirst("Header2"));

        List<String> duplicates = headers.getAll("Duplicate");
        assertEquals(Arrays.asList("Duplicate1", "Duplicate2"), duplicates);
    }
}
