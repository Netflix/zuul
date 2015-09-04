package com.netflix.zuul.context;

import org.junit.Test;

import static org.junit.Assert.*;

public class SessionContextTest {

    @Test
    public void testBoolean()
    {
        SessionContext context = new SessionContext();
        assertEquals(context.getBoolean("boolean_test"), Boolean.FALSE);
        assertEquals(context.getBoolean("boolean_test", true), true);

    }
}