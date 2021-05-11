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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OriginNameTest {
    @Test
    public void getTrustedAuthority() {
        OriginName originName = OriginName.fromVip("woodly-doodly");

        assertFalse(originName.getTrustedAuthority().isPresent());

        OriginName trusted = originName.withTrustedAuthority("westerndigital");

        assertEquals("westerndigital", trusted.getTrustedAuthority().get());
    }
}
