/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.zuul.message;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.junit.jupiter.api.Test;

/**
 * @author Justin Guerra
 * @since 2/22/26
 */
class HeaderNameTest {

    @Test
    void casingTest() {
        HeaderName lowerCase = new HeaderName("x-whatever");
        HeaderName mixed = new HeaderName("X-Whatever");

        assertThat(lowerCase.hashCode()).isEqualTo(mixed.hashCode());
        assertThat(lowerCase).isEqualTo(mixed);
        assertThat(lowerCase.getNormalised()).isEqualTo(mixed.getNormalised());
        assertThat(lowerCase.getName()).isNotEqualTo(mixed.getName());

        assertThat(lowerCase.getName()).isEqualTo("x-whatever");
        assertThat(mixed.getName()).isEqualTo("X-Whatever");
    }
}
