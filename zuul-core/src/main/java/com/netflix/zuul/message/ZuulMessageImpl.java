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

import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.zuul.bytebuf.ByteBufUtils;
import com.netflix.zuul.context.SessionContext;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import rx.Observable;

import java.nio.charset.Charset;

import static org.junit.Assert.assertEquals;

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
    protected boolean hasBody;
    protected Observable<ByteBuf> bodyStream = null;
    protected boolean bodyBuffered = false;
    protected byte[] body = null;

    public ZuulMessageImpl(SessionContext context) {
        this(context, new Headers());
    }

    public ZuulMessageImpl(SessionContext context, Headers headers) {
        this.context = context == null ? new SessionContext() : context;
        this.headers = headers == null ? new Headers() : headers;
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
        this.hasBody = true;

        // Now that body is buffered, if anyone asks for the stream, then give them this wrapper.
        this.bodyStream = Observable.just(Unpooled.wrappedBuffer(this.body));

        this.bodyBuffered = true;
    }

    @Override
    public boolean hasBody()
    {
        return this.hasBody;
    }
    
    @Override
    public void setHasBody(boolean hasBody)
    {
        this.hasBody = hasBody;
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
        else if (null != bodyStream) {
            return ByteBufUtils
                    .aggregate(getBodyStream(), getMaxBodySize())
                    .map(bb -> {
                        // Set the body on Response object.
                        byte[] body = ByteBufUtils.toBytes(bb);
                        setBody(body);
                        bb.release(); // Since this is terminally consuming the buffer.
                        return body;
                    });
        } else {
            return Observable.empty();
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

    @RunWith(MockitoJUnitRunner.class)
    public static class UnitTest
    {
        @Test
        public void testClone()
        {
            SessionContext ctx1 = new SessionContext();
            ctx1.set("k1", "v1");
            Headers headers1 = new Headers();
            headers1.set("k1", "v1");

            ZuulMessage msg1 = new ZuulMessageImpl(ctx1, headers1);
            ZuulMessage msg2 = msg1.clone();

            assertEquals(msg1.getBody(), msg2.getBody());
            assertEquals(msg1.getHeaders(), msg2.getHeaders());
            assertEquals(msg1.getContext(), msg2.getContext());

            // Verify that values of the 2 messages are decoupled.
            msg1.getHeaders().set("k1", "v_new");
            msg1.getContext().set("k1", "v_new");

            assertEquals("v1", msg2.getHeaders().getFirst("k1"));
            assertEquals("v1", msg2.getContext().get("k1"));
        }
    }
}
