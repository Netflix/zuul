/**
 * Copyright 2015 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.zuul.rxnetty.origin;

import com.netflix.zuul.origins.Origin;
import com.netflix.zuul.origins.Origins;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class RxNettyOrigins implements Origins {

    private final ConcurrentMap<String, RxNettyOrigin> localOrigins;
    private final Map<String, RxNettyOrigin> remotePeers;
    private final HostSourceFactory localOriginHostFactory;

    protected RxNettyOrigins(HostSourceFactory localOriginHostFactory, String... originVips) {
        this.localOriginHostFactory = localOriginHostFactory;
        ConcurrentMap<String, RxNettyOrigin> localHolder = new ConcurrentHashMap<>();
        for (String originVip : originVips) {
            localHolder.put(originVip, newOrigin(originVip, localOriginHostFactory));
        }
        localOrigins = localHolder;
        remotePeers = Collections.emptyMap();
    }

    protected RxNettyOrigins(ConcurrentMap<String, RxNettyOrigin> localOrigins, Map<String, RxNettyOrigin> remotePeers,
                             HostSourceFactory localOriginHostFactory) {
        this.localOrigins = localOrigins;
        this.localOriginHostFactory = localOriginHostFactory;
        this.remotePeers = Collections.unmodifiableMap(remotePeers);
    }

    @Override
    public boolean hasOrigin(String name) {
        return localOrigins.containsKey(name);
    }

    @Override
    public Origin getOrigin(String name) {
        if (!hasOrigin(name)) {
            throw new NoSuchElementException("No origin with name: " + name + " exists.");
        }
        return localOrigins.get(name);
    }

    @Override
    public boolean hasRemotePeer(String peerRegion) {
        return remotePeers.containsKey(peerRegion);
    }

    @Override
    public Origin getRemotePeer(String peerRegion) {
        return remotePeers.get(peerRegion);
    }

    @Override
    public Origin createOriginIfAbsent(String name) {
        if (!hasOrigin(name)) {
            RxNettyOrigin newOrigin = newOrigin(name, localOriginHostFactory);
            RxNettyOrigin existing = localOrigins.putIfAbsent(name, newOrigin);
            if (null == existing) {
                return newOrigin;
            } else {
                return existing;
            }
        } else {
            return getOrigin(name);
        }
    }

    /**
     * Adds the passed peer and returns a new instance of {@link RxNettyOrigins} containing all the existing origins and
     * peers along with this newly added peer.
     *
     * @param peerRegion Region for the peer.
     * @param peer Peer.
     *
     * @return A new instance of {@link RxNettyOrigins}
     */
    public RxNettyOrigins addRemotePeer(String peerRegion, RxNettyOrigin peer) {
        Map<String, RxNettyOrigin> peers = new HashMap<>(remotePeers);
        peers.put(peerRegion.trim(), peer);

        return new RxNettyOrigins(localOrigins, peers, localOriginHostFactory);
    }

    /**
     * Creates a new instance of {@link RxNettyOrigins} containing origins with the passed {@code originVips}. The
     * origins are instantiated using the passed {@link HostSourceFactory}
     *
     * @param hostSourceFactory Host source factory to create origins.
     * @param originVips Vips for the origin. Trimmed {@code originVip} will be used as the name for the origins, which
     * are queried by {@link #getOrigin(String)}
     *
     * @return A new {@link RxNettyOrigins} instance.
     */
    public static RxNettyOrigins forOrigins(HostSourceFactory hostSourceFactory, String... originVips) {
        return new RxNettyOrigins(hostSourceFactory, originVips);
    }

    protected RxNettyOrigin newOrigin(String vip, HostSourceFactory hostSourceFactory) {
        return new RxNettyOrigin(vip, hostSourceFactory.call(vip));
    }
}
