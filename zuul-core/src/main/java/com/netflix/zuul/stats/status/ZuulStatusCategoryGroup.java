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

package com.netflix.zuul.stats.status;

import com.google.errorprone.annotations.Immutable;

/**
 * Zuul Status Category Group
 *
 * Author: Arthur Gonigberg
 * Date: December 20, 2017
 */
@Immutable
public enum ZuulStatusCategoryGroup implements StatusCategoryGroup {
    SUCCESS(1),
    FAILURE(2);

    private final int id;

    ZuulStatusCategoryGroup(int id) {
        this.id = id;
    }

    @Override
    public int getId() {
        return id;
    }
}
