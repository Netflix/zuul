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
package com.netflix.zuul.message;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.netty.common.ByteBufUtil;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.message.http.HttpHeaderNames;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: michaels@netflix.com
 * Date: 2/20/15
 * Time: 3:10 PM
 */
public class ZuulMessageImpl implements ZuulMessage {
    protected static final DynamicIntProperty MAX_BODY_SIZE_PROP =
            DynamicPropertyFactory.getInstance().getIntProperty("zuul.message.body.max.size", 25 * 1000 * 1024);

    protected final SessionContext context;
    protected Headers headers;

    private boolean hasBody;
    private boolean bodyBufferedCompletely;
    private final List<HttpContent> bodyChunks;

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
    public void bufferBodyContents(HttpContent chunk) {
        setHasBody(true);
        ByteBufUtil.touch(chunk, "ZuulMessage buffering body content.");
        bodyChunks.add(chunk);
        if (chunk instanceof LastHttpContent) {
            ByteBufUtil.touch(chunk, "ZuulMessage buffering body content complete.");
            bodyBufferedCompletely = true;
        }
    }

    private void setContentLength(int length) {
        headers.remove(HttpHeaderNames.TRANSFER_ENCODING);
        headers.set(HttpHeaderNames.CONTENT_LENGTH, Integer.toString(length));
    }

    @Override
    public void setBodyAsText(String bodyText) {
        disposeBufferedBody();
        if (!Strings.isNullOrEmpty(bodyText)) {
            byte[] bytes = bodyText.getBytes(Charsets.UTF_8);
            bufferBodyContents(new DefaultLastHttpContent(Unpooled.wrappedBuffer(bytes)));
            setContentLength(bytes.length);
        } else {
            bufferBodyContents(new DefaultLastHttpContent());
            setContentLength(0);
        }
    }

    @Override
    public void setBody(byte[] body) {
        disposeBufferedBody();
        if (body != null && body.length > 0) {
            ByteBuf content = Unpooled.copiedBuffer(body);
            bufferBodyContents(new DefaultLastHttpContent(content));
            setContentLength(body.length);
        } else {
            bufferBodyContents(new DefaultLastHttpContent());
            setContentLength(0);
        }
    }

    @Override
    public String getBodyAsText() {
        byte[] body = getBody();
        return (body != null && body.length > 0) ? new String(getBody(), Charsets.UTF_8) : null;
    }

    @Override
    public byte[] getBody() {
        if (bodyChunks.size() == 0) {
            return null;
        }

        int size = this.getBodyLength();
        byte[] body = new byte[size];
        int offset = 0;
        for (HttpContent chunk : bodyChunks) {
            ByteBuf content = chunk.content();
            int len = content.writerIndex(); // writer idx tracks the total readable bytes in the buffer
            content.getBytes(0, body, offset, len);
            offset += len;
        }
        return body;
    }

    @Override
    public int getBodyLength() {
        int size = 0;
        for (HttpContent chunk : bodyChunks) {
            // writer index tracks the total number of bytes written to the buffer regardless of buffer reads
            size += chunk.content().writerIndex();
        }
        return size;
    }

    @Override
    public Iterable<HttpContent> getBodyContents() {
        return Collections.unmodifiableList(bodyChunks);
    }

    @Override
    public void resetBodyReader() {
        for (HttpContent chunk : bodyChunks) {
            chunk.content().resetReaderIndex();
        }
    }

    @Override
    public boolean finishBufferedBodyIfIncomplete() {
        if (!bodyBufferedCompletely) {
            bufferBodyContents(new DefaultLastHttpContent());
            return true;
        }
        return false;
    }

    @Override
    public void disposeBufferedBody() {
        bodyChunks.forEach(chunk -> {
            if ((chunk != null) && (chunk.refCnt() > 0)) {
                ByteBufUtil.touch(chunk, "ZuulMessage disposing buffered body");
                chunk.release();
            }
        });
        bodyChunks.clear();
    }

    @Override
    public void runBufferedBodyContentThroughFilter(ZuulFilter<?, ?> filter) {
        // Loop optimized for the common case: Most filters' processContentChunk() return
        // original chunk passed in as is without any processing
        String filterName = filter.filterName();
        for (int i = 0; i < bodyChunks.size(); i++) {
            HttpContent origChunk = bodyChunks.get(i);
            ByteBufUtil.touch(origChunk, "ZuulMessage processing chunk, filter: ", filterName);
            HttpContent filteredChunk = filter.processContentChunk(this, origChunk);
            ByteBufUtil.touch(filteredChunk, "ZuulMessage processing filteredChunk, filter: ", filterName);
            if ((filteredChunk != null) && (filteredChunk != origChunk)) {
                // filter actually did some processing, set the new chunk in and release the old chunk.
                bodyChunks.set(i, filteredChunk);
                int refCnt = origChunk.refCnt();
                if (refCnt > 0) {
                    origChunk.release(refCnt);
                }
            }
        }
    }

    @Override
    public ZuulMessage clone() {
        ZuulMessageImpl copy = new ZuulMessageImpl(context.clone(), Headers.copyOf(headers));
        this.bodyChunks.forEach(chunk -> {
            chunk.retain();
            copy.bufferBodyContents(chunk);
        });
        return copy;
    }

    /**
     * Override this in more specific subclasses to add request/response info for logging purposes.
     */
    @Override
    public String getInfoForLogging() {
        return "ZuulMessage";
    }
}
