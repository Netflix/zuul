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

package com.netflix.netty.common;

import io.netty.util.concurrent.FastThreadLocalThread;

import java.util.concurrent.ThreadFactory;

/**
 * User: Mike Smith
 * Date: 6/8/16
 * Time: 11:49 AM
 */
public class CategorizedThreadFactory implements ThreadFactory
{
    private String category;
    private int num = 0;

    public CategorizedThreadFactory(String category) {
        super();
        this.category = category;
    }

    public Thread newThread(final Runnable r) {
        final FastThreadLocalThread t = new FastThreadLocalThread(r,
                category + "-" + num++);
        return t;
    }
}
