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
import com.netflix.zuul.bytebuf.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import rx.Observable;

/**
 * User: michaels@netflix.com
 * Date: 2/20/15
 * Time: 3:10 PM
 */
public class ZuulMessage implements Cloneable
{
    protected static final DynamicIntProperty MAX_BODY_SIZE_PROP = DynamicPropertyFactory.getInstance().getIntProperty(
            "zuul.message.body.max.size", 25 * 1000 * 1024);

    protected final SessionContext context;
    protected final Headers headers;
    protected Observable<ByteBuf> bodyStream = null;
    protected boolean bodyBuffered = false;
    protected byte[] body = null;

    public ZuulMessage(SessionContext context) {
        this(context, new Headers());
    }

    public ZuulMessage(SessionContext context, Headers headers) {
        this.context = context;
        this.headers = headers == null ? new Headers() : headers;
    }

    public SessionContext getContext() {
        return context;
    }

    public Headers getHeaders() {
        return headers;
    }

    public byte[] getBody()
    {
        return this.body;
    }

    public void setBody(byte[] body)
    {
        this.body = body;

        // Now that body is buffered, if anyone asks for the stream, then give them this wrapper.
        this.bodyStream = Observable.just(Unpooled.wrappedBuffer(this.body));

        this.bodyBuffered = true;
    }

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

    public int getMaxBodySize() {
        return MAX_BODY_SIZE_PROP.get();
    }

    public boolean isBodyBuffered() {
        return bodyBuffered;
    }

    public Observable<ByteBuf> getBodyStream() {
        return bodyStream;
    }

    public void setBodyStream(Observable<ByteBuf> bodyStream) {
        this.bodyStream = bodyStream;
    }

    @Override
    public ZuulMessage clone()
    {
        ZuulMessage copy = new ZuulMessage(context.clone(), headers.clone());
        copy.setBody(body.clone());
        return copy;
    }

    /**
     * Override this in more specific subclasses to add request/response info for logging purposes.
     *
     * @return
     */
    public String getInfoForLogging()
    {
        return "ZuulMessage";
    }
}
