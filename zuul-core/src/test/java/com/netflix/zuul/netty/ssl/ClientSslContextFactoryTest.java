/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.zuul.netty.ssl;

import static org.junit.Assert.assertEquals;

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
