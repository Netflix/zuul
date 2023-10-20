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
