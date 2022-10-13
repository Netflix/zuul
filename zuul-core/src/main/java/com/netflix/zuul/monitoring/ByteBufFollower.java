package com.netflix.zuul.monitoring;

import com.netflix.config.DynamicBooleanProperty;
import com.netflix.zuul.netty.SpectatorUtils;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCountUtil;
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
                if (ReferenceCountUtil.refCnt(next.bytebuf) <= 0) {
                    iterator.remove();
                } else {
                    log.warn("Tracking unclosed ByteBuf: {}", next);
                    SpectatorUtils.newCounter("zuul.bytebuf.follower", next.bytebuf.getClass().toString())
                            .increment();

                    if (SHOULD_RELEASE.get()) {
                        ReferenceCountUtil.safeRelease(next.bytebuf);
                    }
                }
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    private static class SeenBuf {

        private final String entry;
        private final Object bytebuf;

        public SeenBuf(String entry, Object bytebuf) {
            this.entry = entry;
            this.bytebuf = bytebuf;
        }

        @Override
        public String toString() {
            return "SeenBuf{" +
                    "entry='" + entry + '\'' +
                    ", clazz=" + bytebuf.getClass() +
                    ", count=" + ReferenceCountUtil.refCnt(bytebuf) +
                    '}';
        }
    }
}
