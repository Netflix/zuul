/**
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.zuul.netty.server.push;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Maintains client identity to web socket or SSE channel mapping.
 *
 * Created by saroskar on 9/26/16.
 */
@Singleton
public class PushConnectionRegistry {

    private final ConcurrentMap<String, PushConnection> clientPushConnectionMap;
    private final SecureRandom secureTokenGenerator;

    @Inject
    public PushConnectionRegistry() {
        clientPushConnectionMap = new ConcurrentHashMap<>(1024 * 32);
        secureTokenGenerator = new SecureRandom();
    }

    @Nullable
    public PushConnection get(final String clientId) {
        return clientPushConnectionMap.get(clientId);
    }

    public List<PushConnection> getAll() {
        return new ArrayList<>(clientPushConnectionMap.values());
    }

    public String mintNewSecureToken() {
        byte[] tokenBuffer = new byte[15];
        secureTokenGenerator.nextBytes(tokenBuffer);
        return Base64.getUrlEncoder().encodeToString(tokenBuffer);
    }

    public void put(final String clientId, final PushConnection pushConnection) {
        pushConnection.setSecureToken(mintNewSecureToken());
        clientPushConnectionMap.put(clientId, pushConnection);
    }

    public PushConnection remove(final String clientId) {
        final PushConnection pc = clientPushConnectionMap.remove(clientId);
        return pc;
    }

    public int size() {
        return clientPushConnectionMap.size();
    }
}
