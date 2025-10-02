/*
 * Copyright 2024 Netflix, Inc.
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

package com.netflix.zuul.stats.status;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * @author Justin Guerra
 * @since 10/29/24
 */
public class ZuulStatusCategoryTest {

    @Test
    public void categoriesUseUniqueIds() {
        ZuulStatusCategory[] values = ZuulStatusCategory.values();
        Set<String> ids = Arrays.stream(values).map(ZuulStatusCategory::getId).collect(Collectors.toSet());
        assertThat(ids.size()).isEqualTo(values.length);
    }
}
