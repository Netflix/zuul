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
package com.netflix.zuul.message;

import com.google.common.base.Preconditions;
import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.zuul.bytebuf.ByteBufUtils;
import com.netflix.zuul.context.SessionContext;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import rx.Observable;

import java.nio.charset.Charset;

/**
 * User: michaels@netflix.com
 * Date: 2/20/15
 * Time: 3:10 PM
 */
public class ZuulMessageImpl implements ZuulMessage
{
    protected static final DynamicIntProperty MAX_BODY_SIZE_PROP = DynamicPropertyFactory.getInstance().getIntProperty(
            "zuul.message.body.max.size", 25 * 1000 * 1024);
    private static final Charset CS_UTF8 = Charset.forName("UTF-8");

    protected final SessionContext context;
    protected Headers headers;
    protected Observable<ByteBuf> bodyStream = null;
    protected boolean bodyBuffered = false;
    protected byte[] body = null;

    public ZuulMessageImpl(SessionContext context) {
        this(context, new Headers());
    }

    public ZuulMessageImpl(SessionContext context, Headers headers) {
        Preconditions.checkNotNull(context, "Session context can not be null.");
        this.context = context;
        this.headers = headers == null? new Headers() : headers;
    }

    @Override
    public SessionContext getContext() {
        return context;
    }

    @Override
    public Headers getHeaders() {
        return headers;
    }

    @Override
    public void setHeaders(Headers newHeaders) {
        this.headers = newHeaders;
    }

    @Override
    public byte[] getBody()
    {
        return this.body;
    }

    @Override
    public void setBody(byte[] body)
    {
        this.body = body;

        // Now that body is buffered, if anyone asks for the stream, then give them this wrapper.
        this.bodyStream = Observable.just(Unpooled.wrappedBuffer(this.body));

        this.bodyBuffered = true;
    }

    @Override
    public boolean hasBody()
    {
        return bodyStream != null;
    }

    @Override
    public void setBodyAsText(String bodyText, Charset cs)
    {
        setBody(bodyText.getBytes(cs));
    }

    @Override
    public void setBodyAsText(String bodyText)
    {
        setBodyAsText(bodyText, CS_UTF8);
    }

    @Override
    public Observable<byte[]> bufferBody()
    {
        if (isBodyBuffered()) {
            return Observable.just(getBody());
        }
        else {
            return ByteBufUtils
                    .aggregate(getBodyStream(), getMaxBodySize())
                    .map(bb -> {
                        // Set the body on Response object.
                        byte[] body = ByteBufUtils.toBytes(bb);
                        setBody(body);
                        return body;
                    });
        }
    }

    @Override
    public int getMaxBodySize() {
        return MAX_BODY_SIZE_PROP.get();
    }

    @Override
    public boolean isBodyBuffered() {
        return bodyBuffered;
    }

    @Override
    public Observable<ByteBuf> getBodyStream() {
        return bodyStream;
    }

    @Override
    public void setBodyStream(Observable<ByteBuf> bodyStream) {
        this.bodyStream = bodyStream;
    }

    @Override
    public ZuulMessage clone()
    {
        ZuulMessageImpl copy = new ZuulMessageImpl(context.clone(), headers.clone());

        // Clone body bytes if available, but don't try to clone the bodyStream.
        if (body != null) {
            copy.setBody(body.clone());
        }
        return copy;
    }

    /**
     * Override this in more specific subclasses to add request/response info for logging purposes.
     *
     * @return
     */
    @Override
    public String getInfoForLogging()
    {
        return "ZuulMessage";
    }
}
