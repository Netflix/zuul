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

package com.netflix.netty.common.channel.config;

import java.util.HashMap;

/**
 * User: michaels@netflix.com
 * Date: 2/8/17
 * Time: 6:43 PM
 */
public class ChannelConfig implements Cloneable
{
    private final HashMap<ChannelConfigKey, ChannelConfigValue> parameters;

    public ChannelConfig()
    {
        parameters = new HashMap<>();
    }

    public ChannelConfig(HashMap<ChannelConfigKey, ChannelConfigValue> parameters)
    {
        this.parameters = (HashMap<ChannelConfigKey, ChannelConfigValue>) parameters.clone();
    }

    public void add(ChannelConfigValue param)
    {
        this.parameters.put(param.key(), param);
    }

    public <T> void set(ChannelConfigKey key, T value)
    {
        this.parameters.put(key, new ChannelConfigValue<>(key, value));
    }

    public <T> T get(ChannelConfigKey<T> key)
    {
        ChannelConfigValue<T> ccv = parameters.get(key);
        T value = ccv == null ? null : (T) ccv.value();

        if (value == null) {
            value = key.defaultValue();
        }

        return value;
    }

    public <T> ChannelConfigValue<T> getConfig(ChannelConfigKey<T> key)
    {
        return (ChannelConfigValue<T>) parameters.get(key);
    }

    public <T> boolean contains(ChannelConfigKey<T> key)
    {
        return parameters.containsKey(key);
    }

    @Override
    public ChannelConfig clone()
    {
        return new ChannelConfig(parameters);
    }
}
