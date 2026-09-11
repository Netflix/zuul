/*
 * Copyright 2026 Netflix, Inc.
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
package com.netflix.zuul.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

class GzipperTest {

    private static final String BODY = "HELLO-ZUUL-BODY";

    @Test
    void gzipsDirectBuffer() throws Exception {
        ByteBuf buf = Unpooled.directBuffer(64);
        buf.writeBytes(BODY.getBytes(StandardCharsets.UTF_8));

        assertThat(gzipRoundTrip(buf)).isEqualTo(BODY);
    }

    @Test
    void gzipsHeapBuffer() throws Exception {
        assertThat(gzipRoundTrip(Unpooled.copiedBuffer(BODY, StandardCharsets.UTF_8)))
                .isEqualTo(BODY);
    }

    @Test
    void gzipsHeapBufferWhoseReaderIndexHasAdvanced() throws Exception {
        String prefix = "XXXX";
        ByteBuf buf = Unpooled.buffer(64);
        buf.writeBytes(prefix.getBytes(StandardCharsets.UTF_8));
        buf.writeBytes(BODY.getBytes(StandardCharsets.UTF_8));
        buf.skipBytes(prefix.length());

        assertThat(buf.hasArray()).isTrue();
        assertThat(buf.readerIndex()).isEqualTo(prefix.length());
        assertThat(gzipRoundTrip(buf)).isEqualTo(BODY);
    }

    @Test
    void gzipsSliceOfHeapBuffer() throws Exception {
        ByteBuf backing = Unpooled.copiedBuffer("XXXX" + BODY + "YYYY", StandardCharsets.UTF_8);

        assertThat(gzipRoundTrip(backing.slice(4, BODY.length()))).isEqualTo(BODY);
    }

    @Test
    void gzipsAcrossMultipleChunks() throws Exception {
        Gzipper gzipper = new Gzipper();
        gzipper.write(new DefaultLastHttpContent(Unpooled.copiedBuffer("HELLO-", StandardCharsets.UTF_8)));
        gzipper.write(new DefaultLastHttpContent(Unpooled.copiedBuffer("ZUUL-BODY", StandardCharsets.UTF_8)));
        gzipper.finish();

        assertThat(ungzip(gzipper.getByteBuf())).isEqualTo(BODY);
    }

    private static String gzipRoundTrip(ByteBuf buf) throws Exception {
        Gzipper gzipper = new Gzipper();
        gzipper.write(new DefaultLastHttpContent(buf));
        gzipper.finish();
        return ungzip(gzipper.getByteBuf());
    }

    private static String ungzip(ByteBuf gzipped) throws Exception {
        byte[] bytes = new byte[gzipped.readableBytes()];
        gzipped.readBytes(bytes);
        gzipped.release();
        return new String(
                new GZIPInputStream(new ByteArrayInputStream(bytes)).readAllBytes(), StandardCharsets.UTF_8);
    }
}
