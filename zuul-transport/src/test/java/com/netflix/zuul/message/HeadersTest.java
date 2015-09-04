package com.netflix.zuul.message;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class HeadersTest {
    @Test
    public void testCaseInsensitiveKeys_Set()
    {
        Headers headers = new Headers();
        headers.set("Content-Length", "5");
        headers.set("content-length", "10");

        assertEquals("10", headers.getFirst("Content-Length"));
        assertEquals("10", headers.getFirst("content-length"));
        assertEquals(1, headers.get("content-length").size());
    }

    @Test
    public void testCaseInsensitiveKeys_Add()
    {
        Headers headers = new Headers();
        headers.add("Content-Length", "5");
        headers.add("content-length", "10");

        List<String> values = headers.get("content-length");
        assertTrue(values.contains("10"));
        assertTrue(values.contains("5"));
        assertEquals(2, values.size());
    }

    @Test
    public void testCaseInsensitiveKeys_SetIfAbsent()
    {
        Headers headers = new Headers();
        headers.set("Content-Length", "5");
        headers.setIfAbsent("content-length", "10");

        List<String> values = headers.get("content-length");
        assertEquals(1, values.size());
        assertEquals("5", values.get(0));
    }

    @Test
    public void testCaseInsensitiveKeys_PutAll()
    {
        Headers headers = new Headers();
        headers.add("Content-Length", "5");
        headers.add("content-length", "10");

        Headers headers2 = new Headers();
        headers2.putAll(headers);

        List<String> values = headers2.get("content-length");
        assertTrue(values.contains("10"));
        assertTrue(values.contains("5"));
        assertEquals(2, values.size());
    }

}