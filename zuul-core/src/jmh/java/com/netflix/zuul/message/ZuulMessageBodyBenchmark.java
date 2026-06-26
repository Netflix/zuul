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
package com.netflix.zuul.message;

import com.netflix.zuul.context.SessionContext;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpContent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Compares a repeat read of a fully-buffered body served from the materialized cache against re-walking and
 * re-copying the body chunks on every call (the pre-cache behaviour). The gc profiler (added by the jmh task)
 * reports allocation; throughput mode reports speed:
 *
 * ./gradlew --no-daemon :zuul-core:jmh -Pbenchmark=ZuulMessageBodyBenchmark
 *
 * Reports ops/ms (throughput) and gc.alloc.rate.norm (bytes/op). getBodyCached returns the cached array and should
 * allocate ~0 bytes/op regardless of body size; getBodyFresh allocates a new byte[bodySize] each call, mirroring
 * what ZuulMessageImpl.getBody() did before the cache. Any path that reads the same complete body more than once
 * per request hits the cached path.
 */
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(
        value = 1,
        jvmArgsAppend = {"-Xms1g", "-Xmx1g"})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class ZuulMessageBodyBenchmark {

    @Param({"1024", "65536"})
    public int bodySize;

    private ZuulMessageImpl message;
    private HttpContent[] chunks;

    @Setup
    public void setUp() {
        message = new ZuulMessageImpl(new SessionContext(), new Headers());
        byte[] payload = new byte[bodySize];
        ThreadLocalRandom.current().nextBytes(payload);

        // split the body across a few chunks to mirror a real buffered body
        int chunkSize = Math.max(1, bodySize / 4);
        List<HttpContent> built = new ArrayList<>();
        for (int off = 0; off < bodySize; off += chunkSize) {
            int len = Math.min(chunkSize, bodySize - off);
            ByteBuf buf = Unpooled.copiedBuffer(payload, off, len);
            HttpContent content =
                    (off + len >= bodySize) ? new DefaultLastHttpContent(buf) : new DefaultHttpContent(buf);
            message.bufferBodyContents(content);
            built.add(content);
        }
        chunks = built.toArray(new HttpContent[0]);

        // prime the cache so getBodyCached measures the repeat-read path
        message.getBody();
    }

    @Benchmark
    public byte[] getBodyCached() {
        return message.getBody();
    }

    @Benchmark
    public byte[] getBodyFresh() {
        return materializeFresh();
    }

    private byte[] materializeFresh() {
        byte[] body = new byte[bodySize];
        int offset = 0;
        for (HttpContent content : chunks) {
            ByteBuf buf = content.content();
            int len = buf.writerIndex();
            buf.getBytes(0, body, offset, len);
            offset += len;
        }
        return body;
    }
}
