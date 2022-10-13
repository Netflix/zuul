/*
 * Copyright 2022 Netflix, Inc.
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

package com.netflix.zuul.monitoring;

import com.netflix.config.DynamicBooleanProperty;
import com.netflix.zuul.netty.SpectatorUtils;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCountUtil;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ByteBufFollower
 *
 * @author Arthur Gonigberg
 * @since October 13, 2022
 */
public class ByteBufFollower {

    private static final Logger log = LoggerFactory.getLogger(ByteBufFollower.class);

    private static final List<SeenBuf> buffs = Collections.synchronizedList(new ArrayList<>());

    private static final DynamicBooleanProperty SHOULD_RELEASE =
            new DynamicBooleanProperty("zuul.bytebuf.follower.release", false);

    public static void trackByteBuf(String entry, Object bytebuf) {
        // last content frames are never allocated or released but will have refCnt = 1
        if (bytebuf instanceof LastHttpContent) {
            return;
        }

        // only track if ref-counted and unreleased
        if (ReferenceCountUtil.refCnt(bytebuf) > 0) {
            buffs.add(new SeenBuf(entry, bytebuf));
        }
    }

    static {
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            Iterator<SeenBuf> iterator = buffs.iterator();
            while (iterator.hasNext()) {
                SeenBuf next = iterator.next();
                // was already released in the time we've been watching it
                if (next.bytebuf.get() == null || ReferenceCountUtil.refCnt(next.bytebuf.get()) <= 0) {
                    iterator.remove();
                } else {
                    log.warn("Tracking unclosed ByteBuf: {}", next);
                    SpectatorUtils.newCounter("zuul.bytebuf.follower", next.clazz.toString())
                            .increment();

                    if (SHOULD_RELEASE.get()) {
                        ReferenceCountUtil.safeRelease(next.bytebuf.get());
                    }
                }
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    private static class SeenBuf {

        private final String entry;
        // use weak reference to allow garbage collection and leak detection to work properly
        private final WeakReference<Object> bytebuf;
        private final Class<?> clazz;

        public SeenBuf(String entry, Object bytebuf) {
            this.entry = entry;
            this.bytebuf = new WeakReference<>(bytebuf);
            this.clazz = bytebuf.getClass();
        }

        @Override
        public String toString() {
            return "SeenBuf{" +
                    "entry='" + entry + '\'' +
                    ", clazz=" + clazz +
                    ", count=" + (bytebuf.get() == null ? -1 : ReferenceCountUtil.refCnt(bytebuf.get())) +
                    '}';
        }
    }
}
