package com.netflix.zuul.context;

import com.netflix.zuul.origins.OriginManager;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.*;

public class SessionContextTest {

    @Test
    public void testBoolean()
    {
        SessionContext context = new SessionContext(Mockito.mock(OriginManager.class));
        assertEquals(context.getBoolean("boolean_test"), Boolean.FALSE);
        assertEquals(context.getBoolean("boolean_test", true), true);

    }
}