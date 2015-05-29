package com.netflix.zuul.bytebuf;

import io.netty.buffer.ByteBuf;

import java.io.ByteArrayOutputStream;

/**
 * User: michaels@netflix.com
 * Date: 5/28/15
 * Time: 11:22 AM
 */
public class ByteBufUtils
{
    public static byte[] toBytes(ByteBuf bb)
    {
        // Set the body on Request object.
        try {
            int size = bb.readableBytes();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bb.getBytes(0, baos, size);
            return baos.toByteArray();
        }
        catch (Exception e) {
            throw new RuntimeException("Error buffering message body!", e);
        }
    }
}
