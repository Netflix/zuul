package com.netflix.netty.common.proxyprotocol;

import static com.netflix.zuul.netty.server.ssl.SslHandshakeInfoHandler.ATTR_SSL_INFO;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.google.common.collect.ImmutableList;
import com.netflix.netty.common.proxyprotocol.StripUntrustedProxyHeadersHandler.AllowWhen;
import com.netflix.netty.common.ssl.SslHandshakeInfo;
import com.netflix.zuul.netty.server.ssl.SslHandshakeInfoHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.ssl.ClientAuth;
import io.netty.util.Attribute;
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
    @Mock
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

        when(msg.headers()).thenReturn(headers);
        when(headers.get(eq(HttpHeaderNames.HOST))).thenReturn("netflix.com");
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
    public void blacklist_noMatch() throws Exception {
        StripUntrustedProxyHeadersHandler stripHandler = getHandler(AllowWhen.MUTUAL_SSL_AUTH);

        stripHandler.checkBlacklist(msg, ImmutableList.of("netflix.net"));

        verify(stripHandler, never()).stripXFFHeaders(any());
    }

    @Test
    public void blacklist_match() throws Exception {
        StripUntrustedProxyHeadersHandler stripHandler = getHandler(AllowWhen.MUTUAL_SSL_AUTH);

        stripHandler.checkBlacklist(msg, ImmutableList.of("netflix.com"));

        verify(stripHandler).stripXFFHeaders(any());
    }

    private StripUntrustedProxyHeadersHandler getHandler(AllowWhen allowWhen) {
        return spy(new StripUntrustedProxyHeadersHandler(allowWhen));
    }

}