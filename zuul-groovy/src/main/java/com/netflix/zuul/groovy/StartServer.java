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
package com.netflix.zuul.groovy;

import com.netflix.zuul.FilterStore;
import com.netflix.zuul.ZuulServer;

import java.io.File;

public class StartServer {
    static final int DEFAULT_PORT = 8090;

    public static void main(final String[] args) {
        FilterStore filterStore = new GroovyFileSystemFilterStore(new File("zuul-groovy/src/main/groovy/com/netflix/zuul/groovy/filter"), 15L);
        ZuulServer.start(DEFAULT_PORT, filterStore);
    }
}