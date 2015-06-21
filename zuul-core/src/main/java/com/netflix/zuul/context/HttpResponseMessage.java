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
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;
import io.netty.handler.codec.http.ServerCookieEncoder;
import rx.Observable;

/**
 * User: michaels
 * Date: 2/24/15
 * Time: 10:54 AM
 */
public class HttpResponseMessage extends ZuulMessage
{
    private static final DynamicIntProperty MAX_BODY_SIZE_PROP = DynamicPropertyFactory.getInstance().getIntProperty(
            "zuul.HttpResponseMessage.body.max.size", 25 * 1000 * 1024);

    private HttpRequestMessage request;
    private int status;

    public HttpResponseMessage(SessionContext context, HttpRequestMessage request, int defaultStatus)
    {
        super(context);
        this.request = request;
        this.status = defaultStatus;
    }

    public HttpResponseMessage(SessionContext context, Headers headers, HttpRequestMessage request, int status) {
        super(context, headers);
        this.request = request;
        this.status = status;
    }

    public HttpRequestMessage getRequest() {
        return request;
    }

    public int getStatus() {
        return status;
    }
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
        return super.bufferBody()
                .finallyDo(() -> {
                    timing.end();
                });
    }

    public Cookies parseSetCookieHeader(String setCookieValue)
    {
        Cookies cookies = new Cookies();
        for (Cookie cookie : CookieDecoder.decode(setCookieValue)) {
            cookies.add(cookie);
        }
        return cookies;
    }

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

    public void addSetCookie(Cookie cookie)
    {
        getHeaders().set("Set-Cookie", ServerCookieEncoder.encode(cookie));
    }

    @Override
    public ZuulMessage clone()
    {
        return super.clone();
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
}
