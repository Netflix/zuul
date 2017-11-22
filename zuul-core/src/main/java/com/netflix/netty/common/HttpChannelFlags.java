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

package com.netflix.netty.common;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

/**
 * User: michaels@netflix.com
 * Date: 7/10/17
 * Time: 4:29 PM
 */
public class HttpChannelFlags
{
    public static final Flag IN_BROWNOUT = new Flag("_brownout");

    public static final Flag CLOSING = new Flag("_connection_closing");
    public static final Flag CLOSE_AFTER_RESPONSE = new Flag("_connection_close_after_response");

    public static class Flag
    {
        private final AttributeKey<Boolean> attributeKey;

        public Flag(String name)
        {
            attributeKey = AttributeKey.newInstance(name);
        }

        public void set(Channel ch)
        {
            ch.attr(attributeKey).set(Boolean.TRUE);
        }

        public void remove(Channel ch)
        {
            ch.attr(attributeKey).set(null);
        }

        public void set(ChannelHandlerContext ctx)
        {
            set(ctx.channel());
        }

        public boolean get(Channel ch)
        {
            Attribute<Boolean> attr = ch.attr(attributeKey);
            Boolean value = attr.get();
            return (value == null) ? false : value.booleanValue();
        }

        public boolean get(ChannelHandlerContext ctx)
        {
            return get(ctx.channel());
        }
    }
}
