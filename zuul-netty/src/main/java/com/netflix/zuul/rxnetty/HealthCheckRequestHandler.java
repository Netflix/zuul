package com.netflix.zuul.rxnetty;

import com.netflix.zuul.context.HttpRequestMessage;
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

    public HttpResponseMessage handle(HttpRequestMessage request)
    {
        HttpResponseMessage response = new HttpResponseMessage(request.getContext(), request, 200);
        response.getHeaders().set("Content-Type", "text/plain");
        response.setBody("OK".getBytes(CS_UTF8));

        return response;
    }
}
