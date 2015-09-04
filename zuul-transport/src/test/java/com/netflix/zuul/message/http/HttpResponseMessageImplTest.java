package com.netflix.zuul.message.http;

import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.message.Headers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class HttpResponseMessageImplTest {

    @Mock
    private HttpRequestMessage request;

    private HttpResponseMessageImpl response;

    @Before
    public void setup()
    {
        response = new HttpResponseMessageImpl(new SessionContext(), new Headers(), request, 200);
    }

    @Test
    public void testHasSetCookieWithName()
    {
        response.getHeaders().add("Set-Cookie", "c1=1234; Max-Age=-1; Expires=Tue, 01 Sep 2015 22:49:57 GMT; Path=/; Domain=.netflix.com");
        response.getHeaders().add("Set-Cookie", "c2=4567; Max-Age=-1; Expires=Tue, 01 Sep 2015 22:49:57 GMT; Path=/; Domain=.netflix.com");

        assertTrue(response.hasSetCookieWithName("c1"));
        assertTrue(response.hasSetCookieWithName("c2"));
        assertFalse(response.hasSetCookieWithName("XX"));
    }

    @Test
    public void testRemoveExistingSetCookie()
    {
        response.getHeaders().add("Set-Cookie", "c1=1234; Max-Age=-1; Expires=Tue, 01 Sep 2015 22:49:57 GMT; Path=/; Domain=.netflix.com");
        response.getHeaders().add("Set-Cookie", "c2=4567; Max-Age=-1; Expires=Tue, 01 Sep 2015 22:49:57 GMT; Path=/; Domain=.netflix.com");

        response.removeExistingSetCookie("c1");

        assertEquals(1, response.getHeaders().size());
        assertFalse(response.hasSetCookieWithName("c1"));
        assertTrue(response.hasSetCookieWithName("c2"));
    }

}