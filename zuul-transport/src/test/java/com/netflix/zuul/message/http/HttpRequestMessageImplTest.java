package com.netflix.zuul.message.http;

import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.origins.Origins;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.*;

public class HttpRequestMessageImplTest {
    HttpRequestMessage request;

    @Test
    public void testOriginalRequestInfo()
    {
        HttpQueryParams queryParams = new HttpQueryParams();
        queryParams.add("flag", "5");
        Headers headers = new Headers();
        headers.add("Host", "blah.netflix.com");
        request = new HttpRequestMessageImpl(newSessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                                             "192.168.0.2", "https", 7002, "localhost");

        request.storeInboundRequest();
        HttpRequestInfo originalRequest = request.getInboundRequest();

        Assert.assertEquals(request.getPort(), originalRequest.getPort());
        Assert.assertEquals(request.getPath(), originalRequest.getPath());
        Assert.assertEquals(request.getQueryParams().getFirst("flag"), originalRequest.getQueryParams().getFirst("flag"));
        Assert.assertEquals(request.getHeaders().getFirst("Host"), originalRequest.getHeaders().getFirst("Host"));

        request.setPort(8080);
        request.setPath("/another/place");
        request.getQueryParams().set("flag", "20");
        request.getHeaders().set("Host", "wah.netflix.com");

        Assert.assertEquals(7002, originalRequest.getPort());
        Assert.assertEquals("/some/where", originalRequest.getPath());
        Assert.assertEquals("5", originalRequest.getQueryParams().getFirst("flag"));
        Assert.assertEquals("blah.netflix.com", originalRequest.getHeaders().getFirst("Host"));
    }

    @Test
    public void testReconstructURI()
    {
        HttpQueryParams queryParams = new HttpQueryParams();
        queryParams.add("flag", "5");
        Headers headers = new Headers();
        headers.add("Host", "blah.netflix.com");
        request = new HttpRequestMessageImpl(newSessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                                             "192.168.0.2", "https", 7002, "localhost");
        Assert.assertEquals("https://blah.netflix.com:7002/some/where?flag=5", request.reconstructURI());

        queryParams = new HttpQueryParams();
        headers = new Headers();
        headers.add("X-Forwarded-Host", "place.netflix.com");
        headers.add("X-Forwarded-Port", "80");
        request = new HttpRequestMessageImpl(newSessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                                             "192.168.0.2", "http", 7002, "localhost");
        Assert.assertEquals("http://place.netflix.com/some/where", request.reconstructURI());

        queryParams = new HttpQueryParams();
        headers = new Headers();
        headers.add("X-Forwarded-Host", "place.netflix.com");
        headers.add("X-Forwarded-Proto", "https");
        headers.add("X-Forwarded-Port", "443");
        request = new HttpRequestMessageImpl(newSessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                                             "192.168.0.2", "http", 7002, "localhost");
        Assert.assertEquals("https://place.netflix.com/some/where", request.reconstructURI());

        queryParams = new HttpQueryParams();
        headers = new Headers();
        request = new HttpRequestMessageImpl(newSessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                                             "192.168.0.2", "http", 7002, "localhost");
        Assert.assertEquals("http://localhost:7002/some/where", request.reconstructURI());

        queryParams = new HttpQueryParams();
        queryParams.add("flag", "5");
        queryParams.add("flag B", "9");
        headers = new Headers();
        request = new HttpRequestMessageImpl(newSessionContext(), "HTTP/1.1", "POST", "/some%20where", queryParams, headers,
                                             "192.168.0.2", "https", 7002, "localhost");
        Assert.assertEquals("https://localhost:7002/some%20where?flag=5&flag+B=9", request.reconstructURI());
    }

    @Test
    public void testGetOriginalHost()
    {
        HttpQueryParams queryParams = new HttpQueryParams();
        Headers headers = new Headers();
        headers.add("Host", "blah.netflix.com");
        request = new HttpRequestMessageImpl(newSessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                                             "192.168.0.2", "https", 7002, "localhost");
        Assert.assertEquals("blah.netflix.com", request.getOriginalHost());

        headers = new Headers();
        headers.add("Host", "blah.netflix.com");
        headers.add("X-Forwarded-Host", "foo.netflix.com");
        request = new HttpRequestMessageImpl(newSessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                                             "192.168.0.2", "https", 7002, "localhost");
        Assert.assertEquals("foo.netflix.com", request.getOriginalHost());

        headers = new Headers();
        headers.add("X-Forwarded-Host", "foo.netflix.com");
        request = new HttpRequestMessageImpl(newSessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                                             "192.168.0.2", "https", 7002, "localhost");
        Assert.assertEquals("foo.netflix.com", request.getOriginalHost());

        headers = new Headers();
        headers.add("Host", "blah.netflix.com:8080");
        request = new HttpRequestMessageImpl(newSessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                                             "192.168.0.2", "https", 7002, "localhost");
        Assert.assertEquals("blah.netflix.com", request.getOriginalHost());
    }

    @Test
    public void testGetOriginalPort()
    {
        HttpQueryParams queryParams = new HttpQueryParams();
        Headers headers = new Headers();
        request = new HttpRequestMessageImpl(newSessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                                             "192.168.0.2", "https", 7002, "localhost");
        Assert.assertEquals(7002, request.getOriginalPort());

        headers = new Headers();
        headers.add("Host", "blah.netflix.com");
        headers.add("X-Forwarded-Port", "443");
        request = new HttpRequestMessageImpl(newSessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                                             "192.168.0.2", "https", 7002, "localhost");
        Assert.assertEquals(443, request.getOriginalPort());

        headers = new Headers();
        headers.add("Host", "blah.netflix.com:443");
        request = new HttpRequestMessageImpl(newSessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                                             "192.168.0.2", "https", 7002, "localhost");
        Assert.assertEquals(443, request.getOriginalPort());

        headers = new Headers();
        headers.add("Host", "blah.netflix.com:443");
        headers.add("X-Forwarded-Port", "7005");
        request = new HttpRequestMessageImpl(newSessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                                             "192.168.0.2", "https", 7002, "localhost");
        Assert.assertEquals(7005, request.getOriginalPort());
    }

    @Test
    public void testCleanCookieHeaders()
    {
        assertEquals("BlahId=12345; something=67890;", HttpRequestMessageImpl.cleanCookieHeader("BlahId=12345; Secure, something=67890;"));
        assertEquals("BlahId=12345; something=67890;", HttpRequestMessageImpl.cleanCookieHeader("BlahId=12345; something=67890;"));
        assertEquals(" BlahId=12345; something=67890;", HttpRequestMessageImpl.cleanCookieHeader(" Secure, BlahId=12345; Secure, something=67890;"));
        assertEquals("", HttpRequestMessageImpl.cleanCookieHeader(""));
    }

    private static SessionContext newSessionContext() {
        return new SessionContext(Mockito.mock(Origins.class));
    }
}