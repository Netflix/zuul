package com.netflix.zuul.rxnetty;

import com.netflix.zuul.context.HttpResponseMessage;
import com.netflix.zuul.context.SessionContext;

import java.nio.charset.Charset;

/**
 * User: Mike Smith
 * Date: 3/3/15
 * Time: 12:42 PM
 */
public class HealthCheckRequestHandler
{
    private static final Charset CS_UTF8 = Charset.forName("UTF-8");

    public SessionContext handle(SessionContext ctx)
    {
        HttpResponseMessage response = ctx.getHttpResponse();

        response.getHeaders().set("Content-Type", "text/plain");
        response.setStatus(200);
        response.setBody("OK".getBytes(CS_UTF8));

        return ctx;
    }
}
