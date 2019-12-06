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

package com.netflix.zuul.message.http;

import io.netty.handler.codec.http.Cookie;

/**
 * User: Mike Smith
 * Date: 7/16/15
 * Time: 12:45 AM
 */
public interface HttpResponseMessage extends HttpResponseInfo
{
    void setStatus(int status);

    @Override
    int getMaxBodySize();

    /**
     * Adds a set-cookie header.  Use{@link #addSetCookie(io.netty.handler.codec.http.cookie.Cookie)} instead.
     */
    @Deprecated
    default void addSetCookie(Cookie cookie) {
        // TODO(carl-mastrangelo): remove this in 2.1.8
        throw new UnsupportedOperationException();
    }

    /**
     * Adds a set-cookie header, clearing any existing set-cookie headers.
     */
    @Deprecated
    default void setSetCookie(Cookie cookie) {
        // TODO(carl-mastrangelo): remove this in 2.1.8
        throw new UnsupportedOperationException();
    }

    /**
     * Adds a set-cookie header to the response.
     *
     * @since 2.1.7
     */
    default void addSetCookie(io.netty.handler.codec.http.cookie.Cookie cookie) {
        throw new UnsupportedOperationException();
    }


    boolean removeExistingSetCookie(String cookieName);

    /** The mutable request that will be sent to Origin. */
    HttpRequestMessage getOutboundRequest();

    /** The immutable response that was received from Origin. */
    HttpResponseInfo getInboundResponse();

    /** This should be called after response received from Origin, to store
     * a copy of the response as-is. */
    void storeInboundResponse();
}
