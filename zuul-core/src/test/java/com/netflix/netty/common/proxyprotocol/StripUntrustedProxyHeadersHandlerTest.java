/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.netty.common.proxyprotocol;

import static com.netflix.zuul.netty.server.ssl.SslHandshakeInfoHandler.ATTR_SSL_INFO;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.google.common.collect.ImmutableList;
import com.netflix.netty.common.proxyprotocol.StripUntrustedProxyHeadersHandler.AllowWhen;
import com.netflix.netty.common.ssl.SslHandshakeInfo;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.ssl.ClientAuth;
import io.netty.util.AttributeKey;
import io.netty.util.DefaultAttributeMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Strip Untrusted Proxy Headers Handler Test
 *
 * @author Arthur Gonigberg
 * @since May 27, 2020
 */
@RunWith(MockitoJUnitRunner.class)
public class StripUntrustedProxyHeadersHandlerTest {

    @Mock
    private ChannelHandlerContext channelHandlerContext;
    @Mock
    private HttpRequest msg;
    private HttpHeaders headers;
    @Mock
    private Channel channel;
    @Mock
    private SslHandshakeInfo sslHandshakeInfo;


    @Before
    public void before() {
        when(channelHandlerContext.channel()).thenReturn(channel);

        DefaultAttributeMap attributeMap = new DefaultAttributeMap();
        attributeMap.attr(ATTR_SSL_INFO).set(sslHandshakeInfo);
        when(channel.attr(any())).thenAnswer(arg -> attributeMap.attr((AttributeKey) arg.getArguments()[0]));

        headers = new DefaultHttpHeaders();
        when(msg.headers()).thenReturn(headers);
        headers.add(HttpHeaderNames.HOST, "netflix.com");
    }

    @Test
    public void allow_never() throws Exception {
        StripUntrustedProxyHeadersHandler stripHandler = getHandler(AllowWhen.NEVER);

        stripHandler.channelRead(channelHandlerContext, msg);

        verify(stripHandler).stripXFFHeaders(any());
    }

    @Test
    public void allow_always() throws Exception {
        StripUntrustedProxyHeadersHandler stripHandler = getHandler(AllowWhen.ALWAYS);

        stripHandler.channelRead(channelHandlerContext, msg);

        verify(stripHandler, never()).stripXFFHeaders(any());
        verify(stripHandler).checkBlacklist(any(), any());
    }

    @Test
    public void allow_mtls_noCert() throws Exception {
        StripUntrustedProxyHeadersHandler stripHandler = getHandler(AllowWhen.MUTUAL_SSL_AUTH);

        stripHandler.channelRead(channelHandlerContext, msg);

        verify(stripHandler).stripXFFHeaders(any());
    }

    @Test
    public void allow_mtls_cert() throws Exception {
        StripUntrustedProxyHeadersHandler stripHandler = getHandler(AllowWhen.MUTUAL_SSL_AUTH);
        when(sslHandshakeInfo.getClientAuthRequirement()).thenReturn(ClientAuth.REQUIRE);

        stripHandler.channelRead(channelHandlerContext, msg);

        verify(stripHandler, never()).stripXFFHeaders(any());
        verify(stripHandler).checkBlacklist(any(), any());
    }

    @Test
    public void blacklist_noMatch() {
        StripUntrustedProxyHeadersHandler stripHandler = getHandler(AllowWhen.MUTUAL_SSL_AUTH);

        stripHandler.checkBlacklist(msg, ImmutableList.of("netflix.net"));

        verify(stripHandler, never()).stripXFFHeaders(any());
    }

    @Test
    public void blacklist_match() {
        StripUntrustedProxyHeadersHandler stripHandler = getHandler(AllowWhen.MUTUAL_SSL_AUTH);

        stripHandler.checkBlacklist(msg, ImmutableList.of("netflix.com"));

        verify(stripHandler).stripXFFHeaders(any());
    }

    @Test
    public void blacklist_match_casing() {
        StripUntrustedProxyHeadersHandler stripHandler = getHandler(AllowWhen.MUTUAL_SSL_AUTH);

        stripHandler.checkBlacklist(msg, ImmutableList.of("NeTfLiX.cOm"));

        verify(stripHandler).stripXFFHeaders(any());
    }

    @Test
    public void strip_match() {
        StripUntrustedProxyHeadersHandler stripHandler = getHandler(AllowWhen.MUTUAL_SSL_AUTH);

        headers.add("x-forwarded-for", "abcd");
        stripHandler.stripXFFHeaders(msg);

        assertFalse(headers.contains("x-forwarded-for"));
    }

    private StripUntrustedProxyHeadersHandler getHandler(AllowWhen allowWhen) {
        return spy(new StripUntrustedProxyHeadersHandler(allowWhen));
    }

}