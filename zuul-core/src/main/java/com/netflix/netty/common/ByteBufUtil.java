package com.netflix.netty.common;

import static io.netty.util.ResourceLeakDetector.Level.ADVANCED;
import static io.netty.util.ResourceLeakDetector.Level.PARANOID;
import com.google.common.collect.Lists;
import com.netflix.zuul.message.ZuulMessage;
import io.netty.util.ReferenceCounted;
import io.netty.util.ResourceLeakDetector;

/**
 * ByteBufUtil
 *
 * @author Arthur Gonigberg
 * @since October 20, 2022
 */
public class ByteBufUtil {

    private static final boolean isAdvancedLeakDetection =
            Lists.newArrayList(PARANOID, ADVANCED).contains(ResourceLeakDetector.getLevel());

    public static void touch(ReferenceCounted byteBuf, String hint, ZuulMessage msg) {
        if (isAdvancedLeakDetection) {
            byteBuf.touch(hint + msg);
        }
    }

    public static void touch(ReferenceCounted byteBuf, String hint) {
        if (isAdvancedLeakDetection) {
            byteBuf.touch(hint);
        }
    }

}
