/**
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul.filter;

import com.netflix.zuul.ComputationPostFilter;
import com.netflix.zuul.EgressResponse;

public class ExamplePostFilter<T> extends ComputationPostFilter<T> {

    @Override
    public EgressResponse<T> apply(EgressResponse<T> egressResp) {
        System.out.println(this + " post filter");
        return egressResp;
    }

    @Override
    public int getOrder() {
        return 1;
    }
}