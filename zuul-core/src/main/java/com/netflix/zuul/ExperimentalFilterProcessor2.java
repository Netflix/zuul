/*
 *
 *  Copyright 2013-2015 Netflix, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.netflix.zuul;

import javax.inject.Inject;
import javax.inject.Singleton;


/**
 * Experimenting with how to optimise use of Observable/Single when not required (ie. for Sync filters).
 *
 * Also need to find a way to break out of filter chain processing (ie. when choose to throttle or reject requests) more
 * efficiently, as current mechanism is not shedding much load.
 *
 * @author Mike Smith
 */
@Singleton
public class ExperimentalFilterProcessor2 extends FilterProcessorImpl
{
    @Inject
    public ExperimentalFilterProcessor2(FilterLoader loader, FilterUsageNotifier usageNotifier) {
        super(loader, usageNotifier);
    }


}
