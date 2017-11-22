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
import io.netty.util.AttributeKey;

/**
 * User: michaels@netflix.com
 * Date: 2/8/17
 * Time: 2:04 PM
 */
public enum ConnectionCloseType
{
    IMMEDIATE, GRACEFUL, DELAYED_GRACEFUL;


    private static final AttributeKey<ConnectionCloseType> ATTR_CLOSE_TYPE = AttributeKey.newInstance("_conn_close_type");

    public static ConnectionCloseType fromChannel(Channel ch)
    {
        ConnectionCloseType type = ch.attr(ATTR_CLOSE_TYPE).get();
        if (type == null) {
            // Default to immediate.
            type = ConnectionCloseType.IMMEDIATE;
        }
        return type;
    }

    public static void setForChannel(Channel ch, ConnectionCloseType type)
    {
        ch.attr(ATTR_CLOSE_TYPE).set(type);
    }
}
