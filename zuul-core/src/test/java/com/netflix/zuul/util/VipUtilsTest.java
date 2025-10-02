/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.zuul.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link VipUtils}.
 */
class VipUtilsTest {
    @Test
    void testGetVIPPrefix() {
        assertThatThrownBy(() -> {
                    assertThat(VipUtils.getVIPPrefix("api-test.netflix.net:7001"))
                            .isEqualTo("api-test");
                    assertThat(VipUtils.getVIPPrefix("api-test.netflix.net")).isEqualTo("api-test");
                    assertThat(VipUtils.getVIPPrefix("api-test:7001")).isEqualTo("api-test");
                    assertThat(VipUtils.getVIPPrefix("api-test")).isEqualTo("api-test");
                    assertThat(VipUtils.getVIPPrefix("")).isEqualTo("");
                    VipUtils.getVIPPrefix(null);
                })
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testExtractAppNameFromVIP() {
        assertThatThrownBy(() -> {
                    assertThat(VipUtils.extractUntrustedAppNameFromVIP("api-test.netflix.net:7001"))
                            .isEqualTo("api");
                    assertThat(VipUtils.extractUntrustedAppNameFromVIP("api-test-blah.netflix.net:7001"))
                            .isEqualTo("api");
                    assertThat(VipUtils.extractUntrustedAppNameFromVIP("api")).isEqualTo("api");
                    assertThat(VipUtils.extractUntrustedAppNameFromVIP("")).isEqualTo("");
                    VipUtils.extractUntrustedAppNameFromVIP(null);
                })
                .isInstanceOf(NullPointerException.class);
    }
}
