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

package com.netflix.zuul.message.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.net.InetAddresses;
import com.netflix.config.ConfigurationManager;
import com.netflix.zuul.context.CommonContextKeys;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.message.Headers;
import io.netty.channel.local.LocalAddress;
import io.netty.handler.codec.http.cookie.Cookie;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import org.apache.commons.configuration.AbstractConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("AddressSelection")
@ExtendWith(MockitoExtension.class)
class HttpRequestMessageImplTest {

    HttpRequestMessageImpl request;
    private final AbstractConfiguration config = ConfigurationManager.getConfigInstance();

    @AfterEach
    void resetConfig() {
        config.clearProperty("zuul.HttpRequestMessage.host.header.strict.validation");
    }

    @Test
    void testOriginalRequestInfo() {
        HttpQueryParams queryParams = new HttpQueryParams();
        queryParams.add("flag", "5");
        Headers headers = new Headers();
        headers.add("Host", "blah.netflix.com");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost",
                new LocalAddress("777"),
                false);

        request.storeInboundRequest();
        HttpRequestInfo originalRequest = request.getInboundRequest();

        assertThat(originalRequest.getPort()).isEqualTo(request.getPort());
        assertThat(originalRequest.getPath()).isEqualTo(request.getPath());
        assertThat(originalRequest.getQueryParams().getFirst("flag"))
                .isEqualTo(request.getQueryParams().getFirst("flag"));
        assertThat(originalRequest.getHeaders().getFirst("Host"))
                .isEqualTo(request.getHeaders().getFirst("Host"));

        request.setPort(8080);
        request.setPath("/another/place");
        request.getQueryParams().set("flag", "20");
        request.getHeaders().set("Host", "wah.netflix.com");

        assertThat(originalRequest.getPort()).isEqualTo(7002);
        assertThat(originalRequest.getPath()).isEqualTo("/some/where");
        assertThat(originalRequest.getQueryParams().getFirst("flag")).isEqualTo("5");
        assertThat(originalRequest.getHeaders().getFirst("Host")).isEqualTo("blah.netflix.com");
    }

    @Test
    void testReconstructURI() {
        HttpQueryParams queryParams = new HttpQueryParams();
        queryParams.add("flag", "5");
        Headers headers = new Headers();
        headers.add("Host", "blah.netflix.com");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost");
        assertThat(request.reconstructURI()).isEqualTo("https://blah.netflix.com:7002/some/where?flag=5");

        queryParams = new HttpQueryParams();
        headers = new Headers();
        headers.add("X-Forwarded-Host", "place.netflix.com");
        headers.add("X-Forwarded-Port", "80");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "http",
                7002,
                "localhost");
        assertThat(request.reconstructURI()).isEqualTo("http://place.netflix.com/some/where");

        queryParams = new HttpQueryParams();
        headers = new Headers();
        headers.add("X-Forwarded-Host", "place.netflix.com");
        headers.add("X-Forwarded-Proto", "https");
        headers.add("X-Forwarded-Port", "443");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "http",
                7002,
                "localhost");
        assertThat(request.reconstructURI()).isEqualTo("https://place.netflix.com/some/where");

        queryParams = new HttpQueryParams();
        headers = new Headers();
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "http",
                7002,
                "localhost");
        assertThat(request.reconstructURI()).isEqualTo("http://localhost:7002/some/where");

        queryParams = new HttpQueryParams();
        queryParams.add("flag", "5");
        queryParams.add("flag B", "9");
        headers = new Headers();
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some%20where",
                queryParams,
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost");
        assertThat(request.reconstructURI()).isEqualTo("https://localhost:7002/some%20where?flag=5&flag+B=9");
    }

    @Test
    void testReconstructURI_immutable() {
        HttpQueryParams queryParams = new HttpQueryParams();
        queryParams.add("flag", "5");
        Headers headers = new Headers();
        headers.add("Host", "blah.netflix.com");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost",
                new SocketAddress() {},
                true);

        // Check it's the same value 2nd time.
        assertThat(request.reconstructURI()).isEqualTo("https://blah.netflix.com:7002/some/where?flag=5");
        assertThat(request.reconstructURI()).isEqualTo("https://blah.netflix.com:7002/some/where?flag=5");

        // Check that cached on 1st usage.
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost",
                new SocketAddress() {},
                true);
        request = spy(request);
        when(request._reconstructURI()).thenReturn("http://testhost/blah");
        verify(request, times(1))._reconstructURI();
        assertThat(request.reconstructURI()).isEqualTo("http://testhost/blah");
        assertThat(request.reconstructURI()).isEqualTo("http://testhost/blah");

        // Check that throws exception if we try to mutate it.
        try {
            request.setPath("/new-path");
            fail();
        } catch (IllegalStateException e) {
            assertThat(true).isTrue();
        }
    }

    @Test
    void testPathAndQuery() {
        HttpQueryParams queryParams = new HttpQueryParams();
        queryParams.add("flag", "5");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                new Headers(),
                "192.168.0.2",
                "https",
                7002,
                "localhost");

        // Check that value changes.
        assertThat(request.getPathAndQuery()).isEqualTo("/some/where?flag=5");
        request.getQueryParams().add("k", "v");
        assertThat(request.getPathAndQuery()).isEqualTo("/some/where?flag=5&k=v");
        request.setPath("/other");
        assertThat(request.getPathAndQuery()).isEqualTo("/other?flag=5&k=v");
    }

    @Test
    void testPathAndQuery_immutable() {
        HttpQueryParams queryParams = new HttpQueryParams();
        queryParams.add("flag", "5");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                new Headers(),
                "192.168.0.2",
                "https",
                7002,
                "localhost",
                new SocketAddress() {},
                true);

        // Check it's the same value 2nd time.
        assertThat(request.getPathAndQuery()).isEqualTo("/some/where?flag=5");
        assertThat(request.getPathAndQuery()).isEqualTo("/some/where?flag=5");

        // Check that cached on 1st usage.
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                new Headers(),
                "192.168.0.2",
                "https",
                7002,
                "localhost",
                new SocketAddress() {},
                true);
        request = spy(request);
        when(request.generatePathAndQuery()).thenReturn("/blah");
        verify(request, times(1)).generatePathAndQuery();
        assertThat(request.getPathAndQuery()).isEqualTo("/blah");
        assertThat(request.getPathAndQuery()).isEqualTo("/blah");
    }

    @Test
    void testGetOriginalHost_immutable() {
        HttpQueryParams queryParams = new HttpQueryParams();
        Headers headers = new Headers();
        headers.add("Host", "blah.netflix.com");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost",
                new SocketAddress() {},
                true);

        // Check it's the same value 2nd time.
        assertThat(request.getOriginalHost()).isEqualTo("blah.netflix.com");
        assertThat(request.getOriginalHost()).isEqualTo("blah.netflix.com");

        // Update the Host header value and ensure the result didn't change.
        headers.set("Host", "testOriginalHost2");
        assertThat(request.getOriginalHost()).isEqualTo("blah.netflix.com");
    }

    @Test
    void testGetOriginalHost() {
        HttpQueryParams queryParams = new HttpQueryParams();
        Headers headers = new Headers();
        headers.add("Host", "blah.netflix.com");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost");
        assertThat(request.getOriginalHost()).isEqualTo("blah.netflix.com");

        queryParams = new HttpQueryParams();
        headers = new Headers();
        headers.add("Host", "0.0.0.1");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost");
        assertThat(request.getOriginalHost()).isEqualTo("0.0.0.1");

        queryParams = new HttpQueryParams();
        headers = new Headers();
        headers.add("Host", "0.0.0.1:2");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost");
        assertThat(request.getOriginalHost()).isEqualTo("0.0.0.1");

        queryParams = new HttpQueryParams();
        headers = new Headers();
        headers.add("Host", "[::2]");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost");
        assertThat(request.getOriginalHost()).isEqualTo("[::2]");

        queryParams = new HttpQueryParams();
        headers = new Headers();
        headers.add("Host", "[::2]:3");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost");
        assertThat(request.getOriginalHost()).isEqualTo("[::2]");

        headers = new Headers();
        headers.add("Host", "blah.netflix.com");
        headers.add("X-Forwarded-Host", "foo.netflix.com");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost");
        assertThat(request.getOriginalHost()).isEqualTo("foo.netflix.com");

        headers = new Headers();
        headers.add("X-Forwarded-Host", "foo.netflix.com");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost");
        assertThat(request.getOriginalHost()).isEqualTo("foo.netflix.com");

        headers = new Headers();
        headers.add("Host", "blah.netflix.com:8080");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost");
        assertThat(request.getOriginalHost()).isEqualTo("blah.netflix.com");
    }

    @Test
    void testGetOriginalHost_handlesNonRFC2396Hostnames() {
        config.setProperty("zuul.HttpRequestMessage.host.header.strict.validation", false);

        HttpQueryParams queryParams = new HttpQueryParams();
        Headers headers = new Headers();
        headers.add("Host", "my_underscore_endpoint.netflix.com");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost");
        assertThat(request.getOriginalHost()).isEqualTo("my_underscore_endpoint.netflix.com");

        headers = new Headers();
        headers.add("Host", "my_underscore_endpoint.netflix.com:8080");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost");
        assertThat(request.getOriginalHost()).isEqualTo("my_underscore_endpoint.netflix.com");

        headers = new Headers();
        headers.add("Host", "my_underscore_endpoint^including~more-chars.netflix.com");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost");
        assertThat(request.getOriginalHost()).isEqualTo("my_underscore_endpoint^including~more-chars.netflix.com");

        headers = new Headers();
        headers.add("Host", "hostname%5Ewith-url-encoded.netflix.com");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost");
        assertThat(request.getOriginalHost()).isEqualTo("hostname%5Ewith-url-encoded.netflix.com");
    }

    @Test
    void getOriginalHost_failsOnUnbracketedIpv6Address() {
        HttpQueryParams queryParams = new HttpQueryParams();
        Headers headers = new Headers();
        headers.add("Host", "ba::dd");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost");

        assertThatThrownBy(() -> HttpRequestMessageImpl.getOriginalHost(headers, "server"))
                .isInstanceOf(URISyntaxException.class);
    }

    @Test
    void getOriginalHost_fallsBackOnUnbracketedIpv6Address_WithNonStrictValidation() {
        config.setProperty("zuul.HttpRequestMessage.host.header.strict.validation", false);

        HttpQueryParams queryParams = new HttpQueryParams();
        Headers headers = new Headers();
        headers.add("Host", "ba::dd");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "https",
                7002,
                "server");

        assertThat(request.getOriginalHost()).isEqualTo("server");
    }

    @Test
    void testGetOriginalPort() {
        HttpQueryParams queryParams = new HttpQueryParams();
        Headers headers = new Headers();
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost");
        assertThat(request.getOriginalPort()).isEqualTo(7002);

        headers = new Headers();
        headers.add("Host", "blah.netflix.com");
        headers.add("X-Forwarded-Port", "443");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost");
        assertThat(request.getOriginalPort()).isEqualTo(443);

        headers = new Headers();
        headers.add("Host", "blah.netflix.com:443");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost");
        assertThat(request.getOriginalPort()).isEqualTo(443);

        headers = new Headers();
        headers.add("Host", "127.0.0.2:443");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost");
        assertThat(request.getOriginalPort()).isEqualTo(443);

        headers = new Headers();
        headers.add("Host", "127.0.0.2");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost");
        assertThat(request.getOriginalPort()).isEqualTo(7002);

        headers = new Headers();
        headers.add("Host", "[::2]:443");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost");
        assertThat(request.getOriginalPort()).isEqualTo(443);

        headers = new Headers();
        headers.add("Host", "[::2]");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost");
        assertThat(request.getOriginalPort()).isEqualTo(7002);

        headers = new Headers();
        headers.add("Host", "blah.netflix.com:443");
        headers.add("X-Forwarded-Port", "7005");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost");
        assertThat(request.getOriginalPort()).isEqualTo(7005);

        headers = new Headers();
        headers.add("Host", "host_with_underscores.netflix.com:8080");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost");
        assertThat(request.getOriginalPort())
                .as("should fallback to server port")
                .isEqualTo(7002);
    }

    @Test
    void testGetOriginalPort_NonStrictValidation() {
        config.setProperty("zuul.HttpRequestMessage.host.header.strict.validation", false);

        HttpQueryParams queryParams = new HttpQueryParams();
        Headers headers = new Headers();
        headers.add("Host", "host_with_underscores.netflix.com:8080");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost");
        assertThat(request.getOriginalPort()).isEqualTo(8080);

        headers = new Headers();
        headers.add("Host", "host-with-carrots^1.0.0.netflix.com:8080");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost");
        assertThat(request.getOriginalPort()).isEqualTo(8080);

        headers = new Headers();
        headers.add("Host", "host-with-carrots-no-port^1.0.0.netflix.com");
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                queryParams,
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost");
        assertThat(request.getOriginalPort()).isEqualTo(7002);
    }

    @Test
    void getOriginalPort_fallsBackOnUnbracketedIpv6Address() throws URISyntaxException {
        Headers headers = new Headers();
        headers.add("Host", "ba::33");

        assertThat(HttpRequestMessageImpl.getOriginalPort(new SessionContext(), headers, 9999))
                .isEqualTo(9999);
    }

    @Test
    void getOriginalPort_EmptyXFFPort() throws URISyntaxException {
        Headers headers = new Headers();
        headers.add(HttpHeaderNames.X_FORWARDED_PORT, "");

        // Default to using server port
        assertThat(HttpRequestMessageImpl.getOriginalPort(new SessionContext(), headers, 9999))
                .isEqualTo(9999);
    }

    @Test
    void getOriginalPort_respectsProxyProtocol() throws URISyntaxException {
        SessionContext context = new SessionContext();
        context.set(
                CommonContextKeys.PROXY_PROTOCOL_DESTINATION_ADDRESS,
                new InetSocketAddress(InetAddresses.forString("1.1.1.1"), 443));
        Headers headers = new Headers();
        headers.add("X-Forwarded-Port", "6000");
        assertThat(HttpRequestMessageImpl.getOriginalPort(context, headers, 9999))
                .isEqualTo(443);
    }

    @Test
    void testCleanCookieHeaders() {
        assertThat(HttpRequestMessageImpl.cleanCookieHeader("BlahId=12345; Secure, something=67890;"))
                .isEqualTo("BlahId=12345; something=67890;");
        assertThat(HttpRequestMessageImpl.cleanCookieHeader("BlahId=12345; something=67890;"))
                .isEqualTo("BlahId=12345; something=67890;");
        assertThat(HttpRequestMessageImpl.cleanCookieHeader(" Secure, BlahId=12345; Secure, something=67890;"))
                .isEqualTo(" BlahId=12345; something=67890;");
        assertThat(HttpRequestMessageImpl.cleanCookieHeader("")).isEqualTo("");
    }

    @Test
    void shouldPreferClientDestPortWhenInitialized() {
        HttpRequestMessageImpl message = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                new HttpQueryParams(),
                new Headers(),
                "192.168.0.2",
                "https",
                7002,
                "localhost",
                new InetSocketAddress("api.netflix.com", 443),
                true);

        assertThat(Optional.of(443)).isEqualTo(message.getClientDestinationPort());
    }

    @Test
    public void duplicateCookieNames() {
        Headers headers = new Headers();
        headers.add("cookie", "k=v1;k=v2");
        HttpRequestMessageImpl message = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/some/where",
                new HttpQueryParams(),
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost",
                new InetSocketAddress("api.netflix.com", 443),
                true);
        Cookies cookies = message.parseCookies();
        assertThat(cookies.getAll().size()).isEqualTo(2);
        List<Cookie> kCookies = cookies.get("k");
        assertThat(kCookies.size()).isEqualTo(2);
        assertThat(kCookies.get(0).value()).isEqualTo("v1");
        assertThat(kCookies.get(1).value()).isEqualTo("v2");
    }

    @Test
    public void testGetDecodedPath() {
        Headers headers = new Headers();
        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/%C3%B1",
                new HttpQueryParams(),
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost");
        assertThat(request.getPath()).isEqualTo("/%C3%B1");
        assertThat(request.getDecodedPath()).isEqualTo("/ñ");

        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/ñ",
                new HttpQueryParams(),
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost");
        assertThat(request.getPath()).isEqualTo("/ñ");
        assertThat(request.getDecodedPath()).isEqualTo("/ñ");

        request = new HttpRequestMessageImpl(
                new SessionContext(),
                "HTTP/1.1",
                "POST",
                "/path",
                new HttpQueryParams(),
                headers,
                "192.168.0.2",
                "https",
                7002,
                "localhost");
        assertThat(request.getPath()).isEqualTo("/path");
        assertThat(request.getDecodedPath()).isEqualTo("/path");
    }
}
