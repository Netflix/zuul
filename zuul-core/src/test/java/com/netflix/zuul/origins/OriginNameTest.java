/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.zuul.origins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OriginNameTest {
    @Test
    void getAuthority() {
        OriginName trusted = OriginName.fromVipAndApp("woodly-doodly", "westerndigital");

        assertThat(trusted.getAuthority()).isEqualTo("westerndigital");
    }

    @Test
    void getMetrics() {
        OriginName trusted = OriginName.fromVipAndApp("WOODLY-doodly", "westerndigital");

        assertThat(trusted.getMetricId()).isEqualTo("woodly-doodly");
        assertThat(trusted.getNiwsClientName()).isEqualTo("WOODLY-doodly");
    }

    @Test
    void equals() {
        OriginName name1 = OriginName.fromVipAndApp("woodly-doodly", "westerndigital");
        OriginName name2 = OriginName.fromVipAndApp("woodly-doodly", "westerndigital", "woodly-doodly");

        assertThat(name2).isEqualTo(name1);
        assertThat(name2.hashCode()).isEqualTo(name1.hashCode());
    }

    @Test
    @SuppressWarnings("deprecation")
    void equals_legacy_niws() {
        OriginName name1 = OriginName.fromVip("woodly-doodly", "westerndigital");
        OriginName name2 = OriginName.fromVipAndApp("woodly-doodly", "woodly", "westerndigital");

        assertThat(name2).isEqualTo(name1);
        assertThat(name2.hashCode()).isEqualTo(name1.hashCode());
    }

    @Test
    void equals_legacy() {
        OriginName name1 = OriginName.fromVip("woodly-doodly");
        OriginName name2 = OriginName.fromVipAndApp("woodly-doodly", "woodly", "woodly-doodly");

        assertThat(name2).isEqualTo(name1);
        assertThat(name2.hashCode()).isEqualTo(name1.hashCode());
    }

    @Test
    void noNull() {
        assertThatThrownBy(() -> OriginName.fromVipAndApp(null, "app")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> OriginName.fromVipAndApp("vip", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> OriginName.fromVipAndApp(null, "app", "niws"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> OriginName.fromVipAndApp("vip", null, "niws"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> OriginName.fromVipAndApp("vip", "app", null)).isInstanceOf(NullPointerException.class);
    }
}
