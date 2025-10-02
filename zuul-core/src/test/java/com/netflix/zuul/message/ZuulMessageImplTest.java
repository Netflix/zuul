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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.zuul.context.SessionContext;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpContent;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ZuulMessageImplTest {
    private static final String TEXT1 = "Hello World!";
    private static final String TEXT2 = "Goodbye World!";

    @Test
    void testClone() {
        SessionContext ctx1 = new SessionContext();
        ctx1.set("k1", "v1");
        Headers headers1 = new Headers();
        headers1.set("k1", "v1");

        ZuulMessage msg1 = new ZuulMessageImpl(ctx1, headers1);
        ZuulMessage msg2 = msg1.clone();

        assertThat(msg2.getBodyAsText()).isEqualTo(msg1.getBodyAsText());
        assertThat(msg2.getHeaders()).isEqualTo(msg1.getHeaders());
        assertThat(msg2.getContext()).isEqualTo(msg1.getContext());

        // Verify that values of the 2 messages are decoupled.
        msg1.getHeaders().set("k1", "v_new");
        msg1.getContext().set("k1", "v_new");

        assertThat(msg2.getHeaders().getFirst("k1")).isEqualTo("v1");
        assertThat(msg2.getContext().get("k1")).isEqualTo("v1");
    }

    @Test
    void testBufferBody2GetBody() {
        ZuulMessage msg = new ZuulMessageImpl(new SessionContext(), new Headers());
        msg.bufferBodyContents(new DefaultHttpContent(Unpooled.copiedBuffer("Hello ".getBytes(UTF_8))));
        msg.bufferBodyContents(new DefaultLastHttpContent(Unpooled.copiedBuffer("World!".getBytes(UTF_8))));
        String body = new String(msg.getBody(), UTF_8);
        assertThat(msg.hasBody()).isTrue();
        assertThat(msg.hasCompleteBody()).isTrue();
        assertThat(body).isEqualTo(TEXT1);
        assertThat(msg.getHeaders().getAll("Content-Length").size()).isEqualTo(0);
    }

    @Test
    void testBufferBody3GetBody() {
        ZuulMessage msg = new ZuulMessageImpl(new SessionContext(), new Headers());
        msg.bufferBodyContents(new DefaultHttpContent(Unpooled.copiedBuffer("Hello ".getBytes(UTF_8))));
        msg.bufferBodyContents(new DefaultHttpContent(Unpooled.copiedBuffer("World!".getBytes(UTF_8))));
        msg.bufferBodyContents(new DefaultLastHttpContent());
        String body = new String(msg.getBody(), UTF_8);
        assertThat(msg.hasBody()).isTrue();
        assertThat(msg.hasCompleteBody()).isTrue();
        assertThat(body).isEqualTo(TEXT1);
        assertThat(msg.getHeaders().getAll("Content-Length").size()).isEqualTo(0);
    }

    @Test
    void testBufferBody3GetBodyAsText() {
        ZuulMessage msg = new ZuulMessageImpl(new SessionContext(), new Headers());
        msg.bufferBodyContents(new DefaultHttpContent(Unpooled.copiedBuffer("Hello ".getBytes(UTF_8))));
        msg.bufferBodyContents(new DefaultHttpContent(Unpooled.copiedBuffer("World!".getBytes(UTF_8))));
        msg.bufferBodyContents(new DefaultLastHttpContent());
        String body = msg.getBodyAsText();
        assertThat(msg.hasBody()).isTrue();
        assertThat(msg.hasCompleteBody()).isTrue();
        assertThat(body).isEqualTo(TEXT1);
        assertThat(msg.getHeaders().getAll("Content-Length").size()).isEqualTo(0);
    }

    @Test
    void testSetBodyGetBody() {
        ZuulMessage msg = new ZuulMessageImpl(new SessionContext(), new Headers());
        msg.setBody(TEXT1.getBytes(UTF_8));
        String body = new String(msg.getBody(), UTF_8);
        assertThat(body).isEqualTo(TEXT1);
        assertThat(msg.getHeaders().getAll("Content-Length").size()).isEqualTo(1);
        assertThat(msg.getHeaders().getFirst("Content-Length")).isEqualTo(String.valueOf(TEXT1.length()));
    }

    @Test
    void testSetBodyAsTextGetBody() {
        ZuulMessage msg = new ZuulMessageImpl(new SessionContext(), new Headers());
        msg.setBodyAsText(TEXT1);
        String body = new String(msg.getBody(), UTF_8);
        assertThat(msg.hasBody()).isTrue();
        assertThat(msg.hasCompleteBody()).isTrue();
        assertThat(body).isEqualTo(TEXT1);
        assertThat(msg.getHeaders().getAll("Content-Length").size()).isEqualTo(1);
        assertThat(msg.getHeaders().getFirst("Content-Length")).isEqualTo(String.valueOf(TEXT1.length()));
    }

    @Test
    void testSetBodyAsTextGetBodyAsText() {
        ZuulMessage msg = new ZuulMessageImpl(new SessionContext(), new Headers());
        msg.setBodyAsText(TEXT1);
        String body = msg.getBodyAsText();
        assertThat(msg.hasBody()).isTrue();
        assertThat(msg.hasCompleteBody()).isTrue();
        assertThat(body).isEqualTo(TEXT1);
        assertThat(msg.getHeaders().getAll("Content-Length").size()).isEqualTo(1);
        assertThat(msg.getHeaders().getFirst("Content-Length")).isEqualTo(String.valueOf(TEXT1.length()));
    }

    @Test
    void testMultiSetBodyAsTextGetBody() {
        ZuulMessage msg = new ZuulMessageImpl(new SessionContext(), new Headers());
        msg.setBodyAsText(TEXT1);
        String body = new String(msg.getBody(), UTF_8);
        assertThat(msg.hasBody()).isTrue();
        assertThat(msg.hasCompleteBody()).isTrue();
        assertThat(body).isEqualTo(TEXT1);
        assertThat(msg.getHeaders().getAll("Content-Length").size()).isEqualTo(1);
        assertThat(msg.getHeaders().getFirst("Content-Length")).isEqualTo(String.valueOf(TEXT1.length()));

        msg.setBodyAsText(TEXT2);
        body = new String(msg.getBody(), UTF_8);
        assertThat(msg.hasBody()).isTrue();
        assertThat(msg.hasCompleteBody()).isTrue();
        assertThat(body).isEqualTo(TEXT2);
        assertThat(msg.getHeaders().getAll("Content-Length").size()).isEqualTo(1);
        assertThat(msg.getHeaders().getFirst("Content-Length")).isEqualTo(String.valueOf(TEXT2.length()));
    }

    @Test
    void testMultiSetBodyGetBody() {
        ZuulMessage msg = new ZuulMessageImpl(new SessionContext(), new Headers());
        msg.setBody(TEXT1.getBytes(UTF_8));
        String body = new String(msg.getBody(), UTF_8);
        assertThat(msg.hasBody()).isTrue();
        assertThat(msg.hasCompleteBody()).isTrue();
        assertThat(body).isEqualTo(TEXT1);
        assertThat(msg.getHeaders().getAll("Content-Length").size()).isEqualTo(1);
        assertThat(msg.getHeaders().getFirst("Content-Length")).isEqualTo(String.valueOf(TEXT1.length()));

        msg.setBody(TEXT2.getBytes(UTF_8));
        body = new String(msg.getBody(), UTF_8);
        assertThat(msg.hasBody()).isTrue();
        assertThat(msg.hasCompleteBody()).isTrue();
        assertThat(body).isEqualTo(TEXT2);
        assertThat(msg.getHeaders().getAll("Content-Length").size()).isEqualTo(1);
        assertThat(msg.getHeaders().getFirst("Content-Length")).isEqualTo(String.valueOf(TEXT2.length()));
    }

    @Test
    void testResettingBodyReaderIndex() {
        ZuulMessage msg = new ZuulMessageImpl(new SessionContext(), new Headers());
        msg.bufferBodyContents(new DefaultHttpContent(Unpooled.copiedBuffer("Hello ".getBytes(UTF_8))));
        msg.bufferBodyContents(new DefaultLastHttpContent(Unpooled.copiedBuffer("World!".getBytes(UTF_8))));

        // replicate what happens in nettys tls channel writer which moves the reader index on the contained ByteBuf
        for (HttpContent c : msg.getBodyContents()) {
            c.content().readerIndex(c.content().capacity());
        }

        for (HttpContent c : msg.getBodyContents()) {
            assertThat(c.content().isReadable()).isFalse();
            assertThat(c.content().readableBytes()).isEqualTo(0);
        }

        msg.resetBodyReader();

        for (HttpContent c : msg.getBodyContents()) {
            assertThat(c.content().isReadable()).isTrue();
            assertThat(c.content().readableBytes() > 0).isTrue();
        }
    }

    @Test
    void testFetchingBodyReturnsEntireBuffer() {
        ZuulMessage msg = new ZuulMessageImpl(new SessionContext(), new Headers());
        msg.bufferBodyContents(new DefaultHttpContent(Unpooled.copiedBuffer("Hello ".getBytes(UTF_8))));
        msg.bufferBodyContents(new DefaultLastHttpContent(Unpooled.copiedBuffer("World!".getBytes(UTF_8))));

        // move the reader indexes to the end of the content buffers
        for (HttpContent c : msg.getBodyContents()) {
            c.content().readerIndex(c.content().capacity());
        }

        // ensure body returns entire chunk content irregardless of reader index movement above
        assertThat(msg.getBodyLength()).isEqualTo(12);
        assertThat(new String(msg.getBody(), StandardCharsets.UTF_8)).isEqualTo(TEXT1);

        // buffer more content and ensure body returns entire chunk content
        msg.bufferBodyContents(new DefaultLastHttpContent(Unpooled.copiedBuffer(" Bye".getBytes(UTF_8))));

        assertThat(msg.getBodyLength()).isEqualTo(16);
        assertThat(new String(msg.getBody(), StandardCharsets.UTF_8)).isEqualTo("Hello World! Bye");
    }

    @Test
    void testFetchingEmptyBody() {
        ZuulMessage msg = new ZuulMessageImpl(new SessionContext(), new Headers());
        assertThat(msg.getBodyLength()).isEqualTo(0);
        assertThat(msg.getBody()).isNull();

        msg.bufferBodyContents(new DefaultHttpContent(Unpooled.copiedBuffer("".getBytes(UTF_8))));
        assertThat(msg.getBodyLength()).isEqualTo(0);
        assertThat(msg.getBody().length).isEqualTo(0);
    }
}
