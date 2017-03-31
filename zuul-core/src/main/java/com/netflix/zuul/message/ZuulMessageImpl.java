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

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.filters.ZuulFilter;
import io.netty.buffer.*;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

    private boolean hasBody;
    private boolean bodyBufferedCompletely;
    private List<HttpContent> bodyChunks;


    public ZuulMessageImpl(SessionContext context) {
        this(context, new Headers());
    }

    public ZuulMessageImpl(SessionContext context, Headers headers) {
        this.context = context == null ? new SessionContext() : context;
        this.headers = headers == null ? new Headers() : headers;
        this.bodyChunks = new ArrayList<>(16);
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
    public int getMaxBodySize() {
        return MAX_BODY_SIZE_PROP.get();
    }


    @Override
    public void setHasBody(boolean hasBody) {
        this.hasBody = hasBody;
    }

    @Override
    public boolean hasBody() {
        return hasBody;
    }

    @Override
    public boolean hasCompleteBody() {
        return bodyBufferedCompletely;
    }

    @Override
    public void bufferBodyContents(final HttpContent chunk) {
        setHasBody(true);
//        chunk.retain();
        bodyChunks.add(chunk);
        if (chunk instanceof LastHttpContent) {
            bodyBufferedCompletely = true;
        }
    }

    @Override
    public void setBodyAsText(String bodyText) {
        disposeBufferedBody();
        if (! Strings.isNullOrEmpty(bodyText)) {
            final ByteBuf content = Unpooled.copiedBuffer(bodyText.getBytes(Charsets.UTF_8));
            bufferBodyContents(new DefaultLastHttpContent(content));
        } else {
            bufferBodyContents(new DefaultLastHttpContent());
        }
    }

    @Override
    public void setBody(byte[] body) {
        disposeBufferedBody();
        if (body != null && body.length > 0) {
            final ByteBuf content = Unpooled.copiedBuffer(body);
            bufferBodyContents(new DefaultLastHttpContent(content));
        } else {
            bufferBodyContents(new DefaultLastHttpContent());
        }
    }

    @Override
    public String getBodyAsText() {
        final StringBuilder builder = new StringBuilder();
        bodyChunks.forEach(chunk -> builder.append(chunk.content().toString(Charsets.UTF_8)));
        return builder.toString();
    }

    @Override
    public byte[] getBody() {
        if (bodyChunks.size() == 0) {
            return null;
        }

        final CompositeByteBuf cbuff = Unpooled.compositeBuffer();
        bodyChunks.forEach(chunk -> {
            chunk.retain(); //CompositeByteBuf calls release() on ByteBuf
            cbuff.addComponent(true, chunk.content());
        });
        final byte[] bytes = new byte[cbuff.readableBytes()];
        cbuff.readBytes(bytes);
        cbuff.release();
        return bytes;
    }

    @Override
    public int getBodyLength() {
        return bodyChunks.stream().mapToInt((chunk) -> chunk.content().readableBytes()).sum();
    }

    @Override
    public void writeBufferedBodyContent(Channel channel, boolean retainBeyondWrite) {
        bodyChunks.forEach(chunk -> {
            if (retainBeyondWrite) {
                chunk.retain();
            }
            channel.write(chunk);
        });
    }

    @Override
    public boolean finishBufferedBodyIfIncomplete() {
        if (! bodyBufferedCompletely) {
            bufferBodyContents(new DefaultLastHttpContent());
            return true;
        }
        return false;
    }

    @Override
    public void disposeBufferedBody() {
        bodyChunks.forEach(chunk -> {
            final int refCnt = chunk.refCnt();
            if (refCnt > 0) {
                chunk.release(refCnt);
            }
        });
        bodyChunks.clear();
    }

    @Override
    public void runBufferedBodyContentThroughFilter(ZuulFilter filter) {
        //Loop optimized for the common case: Most filters' processContentChunk() return
        // original chunk passed in as is without any processing
        for (int i=0; i < bodyChunks.size(); i++) {
            final HttpContent origChunk = bodyChunks.get(i);
            final HttpContent filteredChunk = filter.processContentChunk(this, origChunk);
            if (filteredChunk != origChunk) {
                //filter actually did some processing, set the new chunk in and release the old chunk.
                bodyChunks.set(i, filteredChunk);
                final int refCnt = origChunk.refCnt();
                if (refCnt > 0) {
                    origChunk.release(refCnt);
                }
            }
        }
    }

    @Override
    public ZuulMessage clone() {
        final ZuulMessageImpl copy = new ZuulMessageImpl(context.clone(), headers.clone());
        this.bodyChunks.forEach(chunk -> {
            chunk.retain();
            copy.bufferBodyContents(chunk);
        });
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

            assertEquals(msg1.getBodyAsText(), msg2.getBodyAsText());
            assertEquals(msg1.getHeaders(), msg2.getHeaders());
            assertEquals(msg1.getContext(), msg2.getContext());

            // Verify that values of the 2 messages are decoupled.
            msg1.getHeaders().set("k1", "v_new");
            msg1.getContext().set("k1", "v_new");

            assertEquals("v1", msg2.getHeaders().getFirst("k1"));
            assertEquals("v1", msg2.getContext().get("k1"));
        }

        @Test
        public void testBufferBody2GetBody() {
            final ZuulMessage msg = new ZuulMessageImpl(new SessionContext(), new Headers());
            msg.bufferBodyContents(new DefaultHttpContent(Unpooled.copiedBuffer("Hello ".getBytes())));
            msg.bufferBodyContents(new DefaultLastHttpContent(Unpooled.copiedBuffer("World!".getBytes())));
            final String body = new String(msg.getBody());
            assertTrue(msg.hasBody());
            assertTrue(msg.hasCompleteBody());
            assertEquals("Hello World!", body);
        }

        @Test
        public void testBufferBody3GetBody() {
            final ZuulMessage msg = new ZuulMessageImpl(new SessionContext(), new Headers());
            msg.bufferBodyContents(new DefaultHttpContent(Unpooled.copiedBuffer("Hello ".getBytes())));
            msg.bufferBodyContents(new DefaultHttpContent(Unpooled.copiedBuffer("World!".getBytes())));
            msg.bufferBodyContents(new DefaultLastHttpContent());
            final String body = new String(msg.getBody());
            assertTrue(msg.hasBody());
            assertTrue(msg.hasCompleteBody());
            assertEquals("Hello World!", body);
        }

        @Test
        public void testBufferBody3GetBodyAsText() {
            final ZuulMessage msg = new ZuulMessageImpl(new SessionContext(), new Headers());
            msg.bufferBodyContents(new DefaultHttpContent(Unpooled.copiedBuffer("Hello ".getBytes())));
            msg.bufferBodyContents(new DefaultHttpContent(Unpooled.copiedBuffer("World!".getBytes())));
            msg.bufferBodyContents(new DefaultLastHttpContent());
            final String body = msg.getBodyAsText();
            assertTrue(msg.hasBody());
            assertTrue(msg.hasCompleteBody());
            assertEquals("Hello World!", body);
        }

        @Test
        public void testSetBodyGetBody() {
            final ZuulMessage msg = new ZuulMessageImpl(new SessionContext(), new Headers());
            msg.setBody("Hello World!".getBytes());
            final String body = new String(msg.getBody());
            assertEquals("Hello World!", body);
        }

        @Test
        public void testSetBodyAsTextGetBody() {
            final ZuulMessage msg = new ZuulMessageImpl(new SessionContext(), new Headers());
            msg.setBodyAsText("Hello World!");
            final String body = new String(msg.getBody());
            assertTrue(msg.hasBody());
            assertTrue(msg.hasCompleteBody());
            assertEquals("Hello World!", body);
        }

        @Test
        public void testSetBodyAsTextGetBodyAsText() {
            final ZuulMessage msg = new ZuulMessageImpl(new SessionContext(), new Headers());
            msg.setBodyAsText("Hello World!");
            final String body = msg.getBodyAsText();
            assertTrue(msg.hasBody());
            assertTrue(msg.hasCompleteBody());
            assertEquals("Hello World!", body);
        }

        @Test
        public void testMultiSetBodyAsTextGetBody() {
            final ZuulMessage msg = new ZuulMessageImpl(new SessionContext(), new Headers());
            msg.setBodyAsText("Hello World!");
            String body = new String(msg.getBody());
            assertTrue(msg.hasBody());
            assertTrue(msg.hasCompleteBody());
            assertEquals("Hello World!", body);
            msg.setBodyAsText("Goodbye World!");
            body = new String(msg.getBody());
            assertTrue(msg.hasBody());
            assertTrue(msg.hasCompleteBody());
            assertEquals("Goodbye World!", body);
        }

        @Test
        public void testMultiSetBodyGetBody() {
            final ZuulMessage msg = new ZuulMessageImpl(new SessionContext(), new Headers());
            msg.setBody("Hello World!".getBytes());
            String body = new String(msg.getBody());
            assertTrue(msg.hasBody());
            assertTrue(msg.hasCompleteBody());
            assertEquals("Hello World!", body);
            msg.setBody("Goodbye World!".getBytes());
            body = new String(msg.getBody());
            assertTrue(msg.hasBody());
            assertTrue(msg.hasCompleteBody());
            assertEquals("Goodbye World!", body);
        }

    }
}
