/*
 * Copyright 2018 Netflix, Inc.
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

import com.netflix.zuul.exception.ZuulException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpContent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

/**
 * Refactored this out of our GZipResponseFilter
 *
 * User: michaels@netflix.com
 * Date: 5/10/16
 * Time: 12:31 PM
 */
public class Gzipper
{
    private final ByteArrayOutputStream baos;
    private final GZIPOutputStream gzos;

    public Gzipper() throws RuntimeException {
        try {
            baos = new ByteArrayOutputStream(256);
            gzos = new GZIPOutputStream(baos, true);
        }
        catch (IOException e) {
            throw new RuntimeException("Error finalizing the GzipOutputstream", e);
        }
    }

    private void write(ByteBuf bb) throws IOException {
        byte[] bytes;
        int offset;
        final int length = bb.readableBytes();
        if (bb.hasArray()) {
            /* avoid memory copy if possible */
            bytes = bb.array();
            offset = bb.arrayOffset();
        } else {
            bytes = new byte[length];
            bb.getBytes(bb.readerIndex(), bytes);
            offset = 0;
        }
        gzos.write(bytes, offset, length);
    }

    public void write(final HttpContent chunk) {
        try {
            write(chunk.content());
            gzos.flush();
        }
        catch(IOException ioEx) {
            throw new ZuulException(ioEx, "Error Gzipping response content chunk", true);
        }
        finally {
            chunk.release();
        }
    }

    public void finish() throws RuntimeException {
        try {
            gzos.finish();
            gzos.flush();
            gzos.close();
        }
        catch (IOException ioEx) {
            throw new ZuulException(ioEx, "Error finalizing the GzipOutputStream", true);
        }
    }

    public ByteBuf getByteBuf() {
        final ByteBuf copy = Unpooled.copiedBuffer(baos.toByteArray());
        baos.reset();
        return copy;
    }

}
