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
package com.netflix.zuul.origins;

import java.util.NoSuchElementException;

/**
 * A holder for different {@link Origin}s to be used by Zuul. There are two types of origins, viz.
 * <ul>
 <li><i>Local:</i> These are the origins that are proxied by Zuul.</li>
 <li><i>Remote Peer:</i> This is another Zuul peer in a different geographical region that is used for cross-regional
 failover.</li>
 </ul>
 *
 * Since, the usage and creation of these two origin types are very different, there are explicit methods to fetch these
 * origins.
 */
public interface Origins {

    /**
     * Asserts whether an origin with the passed name exists.
     *
     * @param name Origin name to check.
     *
     * @return {@code true} if an origin with the passed name exists.
     */
    boolean hasOrigin(String name);

    /**
     * Returns a local origin with the passed {@code name}. The name is usually the service name used to query the
     * origin service from service discovery.
     *
     * @param name Name of the origin.
     *
     * @return Origin with the name.
     *
     * @throws NoSuchElementException If the origin does not exist.
     */
    Origin getOrigin(String name);

    /**
     * Asserts whether a peer for the passed region exists.
     *
     * @param peerRegion Region for the peer.
     *
     * @return {@code true} if a peer for the passed region exists.
     */
    boolean hasRemotePeer(String peerRegion);

    /**
     * Fetches a remote peer for the passed region.
     *
     * @param peerRegion Region for the remote peer.
     *
     * @return Remote peer.
     *
     * @throws NoSuchElementException If the remote peer does not exist for the passed region.
     */
    Origin getRemotePeer(String peerRegion);

    /**
     * Creates an origin for the passed name, if it does not already exist.
     *
     * @param name Name of the origin.
     *
     * @return Returns the origin with the passed name.
     */
    Origin createOriginIfAbsent(String name);

}
