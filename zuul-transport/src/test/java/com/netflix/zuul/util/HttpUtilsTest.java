package com.netflix.zuul.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class HttpUtilsTest {

    @Test
    public void detectsGzip() {
        assertTrue(HttpUtils.isGzipped("gzip"));
    }

    @Test
    public void detectsNonGzip() {
        assertFalse(HttpUtils.isGzipped("identity"));
    }

    @Test
    public void detectsGzipAmongOtherEncodings() {
        assertTrue(HttpUtils.isGzipped("gzip, deflate"));
    }

}