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

import com.netflix.zuul.filterstore.FileSystemPollingFilterStore;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

public class GroovyFileSystemFilterStore<State> extends FileSystemPollingFilterStore<State> {

    private final GroovyCompiler groovyCompiler;

    public GroovyFileSystemFilterStore(File location, long pollingInternalInSeconds) {
        super(location, pollingInternalInSeconds);
        this.groovyCompiler = new GroovyCompiler();
    }

    @Override
    protected FileFilter getFileFilter() {
        return pathname -> {
            try {
                return pathname.getCanonicalPath().endsWith(".groovy");
            } catch (Exception ex) {
                return false;
            }
        };
    }

    @Override
    protected Class<?> getClassFromFilterFile(File f) throws IOException {
        return groovyCompiler.compile(f);
    }
}
