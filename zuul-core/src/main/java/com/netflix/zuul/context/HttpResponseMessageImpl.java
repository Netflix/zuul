/**
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul.context;

import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.zuul.stats.Timing;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;
import io.netty.handler.codec.http.ServerCookieEncoder;
import rx.Observable;

import java.nio.charset.Charset;

/**
 * User: michaels
 * Date: 2/24/15
 * Time: 10:54 AM
 */
public class HttpResponseMessageImpl implements HttpResponseMessage
{
    private static final DynamicIntProperty MAX_BODY_SIZE_PROP = DynamicPropertyFactory.getInstance().getIntProperty(
            "zuul.HttpResponseMessage.body.max.size", 25 * 1000 * 1024);

    private ZuulMessage message;
    private HttpRequestMessage outboundRequest;
    private int status;
    private HttpResponseInfo inboundResponse = null;

    public HttpResponseMessageImpl(SessionContext context, HttpRequestMessage request, int defaultStatus)
    {
        this.message = new ZuulMessageImpl(context, new Headers());
        this.outboundRequest = request;
        this.status = defaultStatus;
    }

    public HttpResponseMessageImpl(SessionContext context, Headers headers, HttpRequestMessage request, int status)
    {
        this.message = new ZuulMessageImpl(context, headers);
        this.outboundRequest = request;
        this.status = status;
    }

    @Override
    public Headers getHeaders()
    {
        return message.getHeaders();
    }

    @Override
    public SessionContext getContext()
    {
        return message.getContext();
    }

    @Override
    public void setHeaders(Headers newHeaders)
    {
        message.setHeaders(newHeaders);
    }

    @Override
    public byte[] getBody()
    {
        return message.getBody();
    }

    @Override
    public void setBody(byte[] body)
    {
        message.setBody(body);
    }

    @Override
    public boolean hasBody()
    {
        return message.hasBody();
    }

    @Override
    public void setBodyAsText(String bodyText, Charset cs)
    {
        message.setBodyAsText(bodyText, cs);
    }

    @Override
    public void setBodyAsText(String bodyText)
    {
        message.setBodyAsText(bodyText);
    }

    @Override
    public boolean isBodyBuffered()
    {
        return message.isBodyBuffered();
    }

    @Override
    public Observable<ByteBuf> getBodyStream()
    {
        return message.getBodyStream();
    }

    @Override
    public void setBodyStream(Observable<ByteBuf> bodyStream)
    {
        message.setBodyStream(bodyStream);
    }

    @Override
    public HttpRequestMessage getRequest() {
        return outboundRequest;
    }

    @Override
    public HttpRequestInfo getInboundRequest() {
        return outboundRequest.getInboundRequest();
    }

    @Override
    public int getStatus() {
        return status;
    }
    @Override
    public void setStatus(int status) {
        this.status = status;
    }

    @Override
    public int getMaxBodySize() {
        return MAX_BODY_SIZE_PROP.get();
    }

    @Override
    public Observable<byte[]> bufferBody()
    {
        // Wrap the buffering of response body in a timer.
        Timing timing = getContext().getTimings().getResponseBodyRead();
        timing.start();
        return message.bufferBody()
                .finallyDo(() -> {
                    timing.end();
                });
    }

    @Override
    public Cookies parseSetCookieHeader(String setCookieValue)
    {
        Cookies cookies = new Cookies();
        for (Cookie cookie : CookieDecoder.decode(setCookieValue)) {
            cookies.add(cookie);
        }
        return cookies;
    }

    @Override
    public boolean hasSetCookieWithName(String cookieName)
    {
        boolean has = false;
        for (String setCookieValue : getHeaders().get("Set-Cookie")) {
            for (Cookie cookie : CookieDecoder.decode(setCookieValue)) {
                if (cookie.getName().equalsIgnoreCase(cookieName)) {
                    has = true;
                    break;
                }
            }
        }
        return has;
    }

    @Override
    public void addSetCookie(Cookie cookie)
    {
        getHeaders().set("Set-Cookie", ServerCookieEncoder.encode(cookie));
    }

    @Override
    public ZuulMessage clone()
    {
        // TODO - not sure if should be cloning the request object here or not....
        return new HttpResponseMessageImpl(getContext().clone(), getHeaders().clone(),
                (HttpRequestMessage) getRequest().clone(), getStatus());
    }

    @Override
    public String getInfoForLogging()
    {
        StringBuilder sb = new StringBuilder()
                .append(getRequest().getInfoForLogging())
                .append(",proxy-status=").append(getStatus())
                ;
        return sb.toString();
    }

    protected HttpResponseInfo copyResponseInfo()
    {
        return (HttpResponseInfo) this.clone();
    }

    @Override
    public void storeInboundResponse()
    {
        inboundResponse = copyResponseInfo();
    }

    @Override
    public HttpResponseInfo getInboundResponse()
    {
        return inboundResponse;
    }
}
