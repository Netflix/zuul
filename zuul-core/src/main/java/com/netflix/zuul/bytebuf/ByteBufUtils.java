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
package com.netflix.zuul.bytebuf;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import rx.Observable;
import rx.observables.StringObservable;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * User: michaels@netflix.com
 * Date: 5/28/15
 * Time: 11:22 AM
 */
public class ByteBufUtils
{
    public static byte[] toBytes(ByteBuf bb)
    {
        // Set the body on Request object.
        try {
            int size = bb.readableBytes();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bb.getBytes(0, baos, size);
            return baos.toByteArray();
        }
        catch (Exception e) {
            throw new RuntimeException("Error buffering message body!", e);
        }
    }

    /**
     * Use Observable.reduce() to create a virtual ByteBuf to buffer all of the message body before continuing.
     *
     * @return
     */
    public static Observable<ByteBuf> aggregate(Observable<ByteBuf> source, int maxBodySize)
    {
        return source.reduce((bb1, bb2) -> {
            // Buffer the body into a single virtual ByteBuf.
            // and apply some max size to this.
            if (bb1.readableBytes() > maxBodySize) {
                throw new RuntimeException("Max message body size exceeded! maxBodySize=" + maxBodySize);
            }
            return Unpooled.wrappedBuffer(bb1, bb2);
        });
    }

    public static Observable<ByteBuf> fromInputStream(InputStream input)
    {
        return StringObservable.from(input)
                .map(bytes -> Unpooled.wrappedBuffer(bytes))
                .defaultIfEmpty(Unpooled.buffer());
    }
}
