package com.netflix.zuul.context;

/**
 * @author Mike Smith
 */
public class SessionContextKeys
{
    public static final String ORIGIN_MANAGER = "origin_manager";

    public static final String OVERRIDE_GZIP_REQUESTED = "overrideGzipRequested";
    public static final String GZIP_RESP_IF_ORIGIN_DIDNT = "gzipResponseIfOriginDidnt";
}

