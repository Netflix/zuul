package com.netflix.zuul.netty.ssl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


/**
 * Tests for {@link ClientSslContextFactory}.
 */
@RunWith(JUnit4.class)
public class ClientSslContextFactoryTest {

    @Test
    public void enableTls13() {
        String[] protos = ClientSslContextFactory.maybeAddTls13(true, "TLSv1.2");

        assertEquals(Arrays.asList("TLSv1.3", "TLSv1.2"), Arrays.asList(protos));
    }

    @Test
    public void disableTls13() {
        String[] protos = ClientSslContextFactory.maybeAddTls13(false, "TLSv1.2");

        assertEquals(Arrays.asList("TLSv1.2"), Arrays.asList(protos));
    }
}
