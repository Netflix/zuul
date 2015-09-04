package com.netflix.zuul.message;

import com.netflix.zuul.context.SessionContext;
import org.junit.Test;

import static org.junit.Assert.*;

public class ZuulMessageImplTest {
    @Test
    public void testClone()
    {
        SessionContext ctx1 = new SessionContext();
        ctx1.set("k1", "v1");
        Headers headers1 = new Headers();
        headers1.set("k1", "v1");

        ZuulMessage msg1 = new ZuulMessageImpl(ctx1, headers1);
        ZuulMessage msg2 = msg1.clone();

        assertEquals(msg1.getBody(), msg2.getBody());
        assertEquals(msg1.getHeaders(), msg2.getHeaders());
        assertEquals(msg1.getContext(), msg2.getContext());

        // Verify that values of the 2 messages are decoupled.
        msg1.getHeaders().set("k1", "v_new");
        msg1.getContext().set("k1", "v_new");

        assertEquals("v1", msg2.getHeaders().getFirst("k1"));
        assertEquals("v1", msg2.getContext().get("k1"));
    }
}