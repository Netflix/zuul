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

import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.filters.ZuulFilter;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import javax.annotation.Nullable;


/**
 * Represents a message that propagates through the Zuul filter chain.
 */
public interface ZuulMessage extends Cloneable {

    /**
     * Returns the session context of this message.
     */
    SessionContext getContext();

    /**
     * Returns the headers for this message.  They may be request or response headers, depending on the underlying type
     * of this object.  For some messages, there may be no headers, such as with chunked requests or responses.  In this
     * case, a non-{@code null} default headers value will be returned.
     */
    Headers getHeaders();

    /**
     * Sets the headers for this message.
     *
     * @throws NullPointerException if newHeaders is {@code null}.
     */
    void setHeaders(Headers newHeaders);

    /**
     * Returns if this message has an attached body.   For requests, this is typically an HTTP POST body.  For
     * responses, this is typically the HTTP response.
     */
    boolean hasBody();

    /**
     *  Declares that this message has a body.   This method is automatically called when {@link #bufferBodyContents}
     *  is invoked.
     */
    void setHasBody(boolean hasBody);

    /**
     * Returns the message body.  If there is no message body, this returns {@code null}.
     */
    @Nullable
    byte[] getBody();

    /**
     * Returns the length of the message body, or {@code 0} if there isn't a message present.
     */
    int getBodyLength();

    /**
     * Sets the message body.  Note: if the {@code body} is {@code null}, this may not reset the body presence as
     * returned by {@link #hasBody}.  The body is considered complete after calling this method.
     */
    void setBody(@Nullable byte[] body);

    /**
     * Sets the message body as UTF-8 encoded text.   Note that this does NOT set any headers related to the
     * Content-Type; callers must set or reset the content type to UTF-8.  The body is considered complete after
     * calling this method.
     */
    void setBodyAsText(@Nullable String bodyText);

    /**
     * Appends an HTTP content chunk to this message.  Callers should be careful not to add multiple chunks that
     * implement {@link LastHttpContent}.
     *
     * @throws NullPointerException if {@code chunk} is {@code null}.
     */
    void bufferBodyContents(HttpContent chunk);

    /**
     * Returns the HTTP content chunks that are part of this message.  Callers should avoid retaining the return value,
     * as the contents may change with subsequent body mutations.
     */
    Iterable<HttpContent> getBodyContents();

    /**
     * Sets the message body to be complete if it was not already so.
     *
     * @return {@code true} if the body was not yet complete, or else false.
     */
    boolean finishBufferedBodyIfIncomplete();

    /**
     * Indicates that the message contains a content chunk the implements {@link LastHttpContent}.
     */
    boolean hasCompleteBody();

    /**
     * Passes the body content chunks through the given filter, and sets them back into this message.
     */
    void runBufferedBodyContentThroughFilter(ZuulFilter filter);

    /**
     * Clears the content chunks of this body, calling {@code release()} in the process.  Users SHOULD call this method
     * when the body content is no longer needed.
     */
    void disposeBufferedBody();

    /**
     * Gets the body of this message as UTF-8 text, or {@code null} if there is no body.
     */
    @Nullable
    String getBodyAsText();

    /**
     * Returns the maximum body size that this message is willing to hold.  This value value should be more than the
     * sum of lengths of the body chunks.  The max body size may not be strictly enforced, and is informational.
     */
    int getMaxBodySize();

    /**
     * Returns a copy of this message.
     */
    ZuulMessage clone();

    /**
     * Returns a string that reprsents this message which is suitable for debugging.
     */
    String getInfoForLogging();
}
