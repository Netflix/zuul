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
package com.netflix.zuul.rxnetty;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.config.DynamicStringMapProperty;
import com.netflix.governator.annotations.WarmUp;
import com.netflix.zuul.origins.Origin;
import com.netflix.zuul.origins.OriginManager;
import io.reactivex.netty.client.Host;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User: michaels@netflix.com
 * Date: 2/20/15
 * Time: 3:04 PM
 */
@Singleton
public class RxNettyOriginManager implements OriginManager
{
    private final static Logger LOG = LoggerFactory.getLogger(RxNettyOriginManager.class);
    private final DynamicStringMapProperty ORIGINS = new DynamicStringMapProperty("zuul.origins", "");

    private final Map<String, Origin> origins = new ConcurrentHashMap<>();

    private final HostSourceFactory hostSourceFactory;

    @Inject
    public RxNettyOriginManager(HostSourceFactory hostSourceFactory)
    {
        if (hostSourceFactory == null) {
            throw new IllegalArgumentException("Host source factory is null.");
        }
        this.hostSourceFactory = hostSourceFactory;
        initialize();
    }

    @WarmUp
    public void initialize()
    {
        try {
            Map<String, String> originMappings = ORIGINS.getMap();
            for (String originName : originMappings.keySet())
            {
                String vip = originMappings.get(originName);
                if (vip != null) {
                    vip = vip.trim();
                    if (vip.length() > 0) {
                        LOG.info("Registering Origin for vip=" + vip);
                        try {
                            initOrigin(originName, vip);
                        }
                        catch (Exception e) {
                            // TODO - resolve why this is failing on first attempts at startup.
                            LOG.error("Error creating origin for vip=" + vip, e);
                        }
                    }
                }
            }
        }
        catch(Exception e) {
            String msg = "Error initialising OriginManager. zuul.origins property=" + String.valueOf(ORIGINS.get());
            LOG.error(msg, e);
            throw new IllegalStateException(msg, e);
        }
    }

    private void initOrigin(String originName, String vip)
    {
        Observable<Host> hosts = hostSourceFactory.call(vip);
        this.origins.put(originName, RxNettyOrigin.newOrigin(originName, vip, hosts));
    }

    @Override
    public Origin getOrigin(String name)
    {
        if (name == null) {
            return null;
        } else {
            return origins.get(name);
        }
    }

    @Override
    public Origin createOrigin(String name, String vip)
    {
        initOrigin(name, vip);
        return getOrigin(name);
    }
}
