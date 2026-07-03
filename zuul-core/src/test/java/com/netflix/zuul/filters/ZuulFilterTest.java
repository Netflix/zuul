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
package com.netflix.zuul.filters;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.zuul.filters.common.GZipResponseFilter;
import com.netflix.zuul.message.ZuulMessage;
import io.netty.handler.codec.http.HttpContent;
import org.junit.jupiter.api.Test;
import rx.Observable;

class ZuulFilterTest {

    @Test
    void baseFilterWithNoOpProcessContentChunkReportsFalse() {
        assertThat(new NoOpBaseFilter().processesContentChunks()).isFalse();
    }

    @Test
    void baseFilterOverridingProcessContentChunkReportsTrue() {
        assertThat(new ChunkBaseFilter().processesContentChunks()).isTrue();
    }

    @Test
    void syncAdapterWithNoOpProcessContentChunkReportsFalse() {
        assertThat(new NoOpSyncAdapter().processesContentChunks()).isFalse();
    }

    @Test
    void syncAdapterOverridingProcessContentChunkReportsTrue() {
        assertThat(new ChunkSyncAdapter().processesContentChunks()).isTrue();
    }

    @Test
    void gzipResponseFilterReportsTrue() {
        assertThat(new GZipResponseFilter().processesContentChunks()).isTrue();
    }

    @Test
    void overridesProcessContentChunkDetectsInterfaceDefault() {
        assertThat(ZuulFilter.overridesProcessContentChunk(NoOpBaseFilter.class))
                .isFalse();
        assertThat(ZuulFilter.overridesProcessContentChunk(ChunkBaseFilter.class))
                .isTrue();
    }

    private static class NoOpBaseFilter extends BaseFilter<ZuulMessage, ZuulMessage> {
        @Override
        public Observable<ZuulMessage> applyAsync(ZuulMessage input) {
            return Observable.just(input);
        }

        @Override
        public FilterType filterType() {
            return FilterType.INBOUND;
        }

        @Override
        public boolean shouldFilter(ZuulMessage msg) {
            return true;
        }
    }

    private static class ChunkBaseFilter extends NoOpBaseFilter {
        @Override
        public HttpContent processContentChunk(ZuulMessage zuulMessage, HttpContent chunk) {
            return chunk;
        }
    }

    private static class NoOpSyncAdapter extends SyncZuulFilterAdapter<ZuulMessage, ZuulMessage> {
        @Override
        public String filterName() {
            return getClass().getName();
        }

        @Override
        public ZuulMessage apply(ZuulMessage input) {
            return input;
        }

        @Override
        public ZuulMessage getDefaultOutput(ZuulMessage input) {
            return input;
        }
    }

    private static class ChunkSyncAdapter extends NoOpSyncAdapter {
        @Override
        public HttpContent processContentChunk(ZuulMessage zuulMessage, HttpContent chunk) {
            return chunk;
        }
    }
}
