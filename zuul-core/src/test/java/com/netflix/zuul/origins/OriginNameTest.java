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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OriginNameTest {
    @Test
    void getAuthority() {
        OriginName trusted = OriginName.fromVipAndApp("woodly-doodly", "westerndigital");

        assertEquals("westerndigital", trusted.getAuthority());
    }

    @Test
    void getMetrics() {
        OriginName trusted = OriginName.fromVipAndApp("WOODLY-doodly", "westerndigital");

        assertEquals("woodly-doodly", trusted.getMetricId());
        assertEquals("WOODLY-doodly", trusted.getNiwsClientName());
    }


    @Test
    void equals() {
        OriginName name1 = OriginName.fromVipAndApp("woodly-doodly", "westerndigital");
        OriginName name2 = OriginName.fromVipAndApp("woodly-doodly", "westerndigital", "woodly-doodly");

        assertEquals(name1, name2);
        assertEquals(name1.hashCode(), name2.hashCode());
    }

    @Test
    @SuppressWarnings("deprecation")
    void equals_legacy_niws() {
        OriginName name1 = OriginName.fromVip("woodly-doodly", "westerndigital");
        OriginName name2 = OriginName.fromVipAndApp("woodly-doodly", "woodly", "westerndigital");

        assertEquals(name1, name2);
        assertEquals(name1.hashCode(), name2.hashCode());
    }

    @Test
    void equals_legacy() {
        OriginName name1 = OriginName.fromVip("woodly-doodly");
        OriginName name2 = OriginName.fromVipAndApp("woodly-doodly", "woodly", "woodly-doodly");

        assertEquals(name1, name2);
        assertEquals(name1.hashCode(), name2.hashCode());
    }

    @Test
    void noNull() {
        assertThrows(NullPointerException.class, () -> OriginName.fromVipAndApp(null, "app"));
        assertThrows(NullPointerException.class, () -> OriginName.fromVipAndApp("vip", null));
        assertThrows(NullPointerException.class, () -> OriginName.fromVipAndApp(null, "app", "niws"));
        assertThrows(NullPointerException.class, () -> OriginName.fromVipAndApp("vip", null, "niws"));
        assertThrows(NullPointerException.class, () -> OriginName.fromVipAndApp("vip", "app", null));
    }
}
