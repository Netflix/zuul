/*
 * Copyright 2019 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */

package com.netflix.zuul.message;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.netflix.zuul.context.SessionContext;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ZuulMessageImplTest {

    @Test
    public void testClone() {
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
