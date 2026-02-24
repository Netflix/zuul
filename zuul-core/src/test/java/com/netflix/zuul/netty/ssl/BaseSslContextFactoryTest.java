/*
 * Copyright 2022 Netflix, Inc.
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

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslProvider;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BaseSslContextFactory}.
 */
class BaseSslContextFactoryTest {
    @Test
    void testDefaultSslProviderIsOpenSsl() {
        assertThat(BaseSslContextFactory.chooseSslProvider()).isEqualTo(SslProvider.OPENSSL);
    }

    @Test
    void defaultNamedGroupsMatchNettyDefaults() throws Exception {
        Field nettyDefaultsField = OpenSsl.class.getDeclaredField("DEFAULT_NAMED_GROUPS");
        nettyDefaultsField.setAccessible(true);
        String[] nettyDefaultNamedGroups = (String[]) nettyDefaultsField.get(null);

        Field zuulField = BaseSslContextFactory.class.getDeclaredField("DEFAULT_NAMED_GROUPS");
        zuulField.setAccessible(true);
        String[] zuulGroups = (String[]) zuulField.get(null);

        assertThat(zuulGroups).as("should match netty defaults").containsExactly(nettyDefaultNamedGroups);
    }
}
