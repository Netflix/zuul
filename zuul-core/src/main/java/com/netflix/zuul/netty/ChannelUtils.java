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

package com.netflix.zuul.netty;

import com.netflix.zuul.passport.CurrentPassport;
import io.netty.channel.Channel;

public class ChannelUtils
{
    public static String channelInfoForLogging(Channel ch)
    {
        if (ch == null) {
            return "null";
        }
        
        String channelInfo = ch.toString() 
                + ", active=" + ch.isActive()
                + ", open=" + ch.isOpen()
                + ", registered=" + ch.isRegistered()
                + ", writable=" + ch.isWritable()
                + ", id=" + ch.id();
        
        CurrentPassport passport = CurrentPassport.fromChannel(ch);
        return "Channel: " + channelInfo + ", Passport: " + String.valueOf(passport);
    }
}
