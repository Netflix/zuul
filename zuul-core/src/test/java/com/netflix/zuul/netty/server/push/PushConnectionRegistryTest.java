/*
 * Copyright 2023 Netflix, Inc.
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

package com.netflix.zuul.netty.server.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PushConnectionRegistryTest {
    private PushConnectionRegistry pushConnectionRegistry;

    private PushConnection pushConnection;

    @BeforeEach
    void setUp() {
        pushConnectionRegistry = new PushConnectionRegistry();
        pushConnection = mock(PushConnection.class);
    }

    @Test
    void testPutAndGet() {
        assertNull(pushConnectionRegistry.get("clientId1"));

        pushConnectionRegistry.put("clientId1", pushConnection);

        assertEquals(pushConnection, pushConnectionRegistry.get("clientId1"));
    }

    @Test
    void testGetAll() {
        pushConnectionRegistry.put("clientId1", pushConnection);
        pushConnectionRegistry.put("clientId2", pushConnection);

        List<PushConnection> connections = pushConnectionRegistry.getAll();

        assertEquals(2, connections.size());
    }

    @Test
    void testMintNewSecureToken() {
        String token = pushConnectionRegistry.mintNewSecureToken();

        assertNotNull(token);
        assertEquals(20, token.length()); // 15 bytes become 20 characters when Base64-encoded
    }

    @Test
    void testPutAssignsTokenToConnection() {
        pushConnectionRegistry.put("clientId1", pushConnection);

        verify(pushConnection).setSecureToken(anyString());
    }

    @Test
    void testRemove() {
        pushConnectionRegistry.put("clientId1", pushConnection);

        assertEquals(pushConnection, pushConnectionRegistry.remove("clientId1"));
        assertNull(pushConnectionRegistry.get("clientId1"));
    }

    @Test
    void testSize() {
        assertEquals(0, pushConnectionRegistry.size());

        pushConnectionRegistry.put("clientId1", pushConnection);

        assertEquals(1, pushConnectionRegistry.size());
    }
}
