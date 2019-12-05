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

import com.google.common.annotations.VisibleForTesting;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import javax.annotation.Nullable;

/**
 * Stores the source IP address as an attribute of the channel. This has the advantage of allowing
 * us to overwrite it if we have more info (eg. ELB sends a HAProxyMessage with info of REAL source
 * host + port).
 *
 * User: michaels@netflix.com
 * Date: 4/14/16
 * Time: 4:29 PM
 */
@ChannelHandler.Sharable
public final class SourceAddressChannelHandler extends ChannelInboundHandlerAdapter {
    /**
     * Indicates the actual source (remote) address of the channel.  This can be different than the
     * one {@link Channel} returns if the connection is being proxied.  (e.g. over HAProxy)
     */
    public static final AttributeKey<SocketAddress> ATTR_REMOTE_ADDR = AttributeKey.newInstance("_remote_addr");

    /** Use {@link #ATTR_REMOTE_ADDR} instead. */
    @Deprecated
    public static final AttributeKey<InetSocketAddress> ATTR_SOURCE_INET_ADDR =
            AttributeKey.newInstance("_source_inet_addr");

    /**
     * The host address of the source.   This is derived from {@link #ATTR_REMOTE_ADDR}.   If the
     * address is an IPv6 address, the scope identifier is absent.
     */
    public static final AttributeKey<String> ATTR_SOURCE_ADDRESS = AttributeKey.newInstance("_source_address");

    /**
     * Indicates the actual source (remote) port of the channel, if present.  This can be different
     * than the one {@link Channel} returns if the connection is being proxies.  (e.g. over
     * HAProxy).
     * @deprecated use {@link #ATTR_REMOTE_ADDR} instead, and check if it is an {@code
     *      InetSocketAddress}.
     */
    @Deprecated
    public static final AttributeKey<Integer> ATTR_SOURCE_PORT = AttributeKey.newInstance("_source_port");

    /**
     * Indicates the local address of the channel.  This can be different than the
     * one {@link Channel} returns if the connection is being proxied.  (e.g. over HAProxy)
     */
    public static final AttributeKey<SocketAddress> ATTR_LOCAL_ADDR = AttributeKey.newInstance("_local_addr");

    /**
     * Use {@link #ATTR_LOCAL_ADDR} instead.
     */
    @Deprecated
    public static final AttributeKey<InetSocketAddress> ATTR_LOCAL_INET_ADDR =
            AttributeKey.newInstance("_local_inet_addr");

    /**
     * The local address of this channel.  This is derived from {@code channel.localAddress()}, or from the
     * Proxy Protocol preface if provided.  If the address is an IPv6 address, the scope identifier is absent.
     * Unlike {@link #ATTR_SERVER_LOCAL_ADDRESS}, this value is overwritten with the Proxy Protocol local address
     * (e.g. the LB's local address), if enabled.
     */
    public static final AttributeKey<String> ATTR_LOCAL_ADDRESS = AttributeKey.newInstance("_local_address");

    /**
     * The port number of the local socket, or {@code -1} if not appropriate.  Use {@link #ATTR_LOCAL_ADDR} instead.
     */
    @Deprecated
    public static final AttributeKey<Integer> ATTR_LOCAL_PORT = AttributeKey.newInstance("_local_port");


    /**
     * The actual local address of the channel, in string form.  If the address is an IPv6 address, the scope
     * identifier is absent.  Unlike {@link #ATTR_LOCAL_ADDRESS}, this is not overwritten by the Proxy Protocol message
     * if present.
     *
     * @deprecated Use {@code channel.localAddress()}  instead.
     */
    @Deprecated
    public static final AttributeKey<String> ATTR_SERVER_LOCAL_ADDRESS = AttributeKey.newInstance("_server_local_address");

    /**
     * The port number of the local socket, or {@code -1} if not appropriate.  Unlike {@link #ATTR_LOCAL_PORT}, this
     * is not overwritten by the Proxy Protocol message if present.
     *
     * @deprecated Use {@code channel.localAddress()}  instead.
     */
    @Deprecated
    public static final AttributeKey<Integer> ATTR_SERVER_LOCAL_PORT = AttributeKey.newInstance("_server_local_port");

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception
    {
        ctx.channel().attr(ATTR_REMOTE_ADDR).set(ctx.channel().remoteAddress());
        InetSocketAddress sourceAddress = sourceAddress(ctx.channel());
        ctx.channel().attr(ATTR_SOURCE_INET_ADDR).setIfAbsent(sourceAddress);
        ctx.channel().attr(ATTR_SOURCE_ADDRESS).setIfAbsent(getHostAddress(sourceAddress));
        ctx.channel().attr(ATTR_SOURCE_PORT).setIfAbsent(sourceAddress.getPort());

        ctx.channel().attr(ATTR_LOCAL_ADDR).set(ctx.channel().localAddress());
        InetSocketAddress localAddress = localAddress(ctx.channel());
        ctx.channel().attr(ATTR_LOCAL_INET_ADDR).setIfAbsent(localAddress);
        ctx.channel().attr(ATTR_LOCAL_ADDRESS).setIfAbsent(getHostAddress(localAddress));
        ctx.channel().attr(ATTR_LOCAL_PORT).setIfAbsent(localAddress.getPort());
        // ATTR_LOCAL_ADDRESS and ATTR_LOCAL_PORT get overwritten with what is received in
        // Proxy Protocol (via the LB), so set local server's address, port explicitly
        ctx.channel().attr(ATTR_SERVER_LOCAL_ADDRESS).setIfAbsent(localAddress.getAddress().getHostAddress());
        ctx.channel().attr(ATTR_SERVER_LOCAL_PORT).setIfAbsent(localAddress.getPort());

        super.channelActive(ctx);
    }

    /**
     * Returns the String form of a socket address, or {@code null} if there isn't one.
     */
    @VisibleForTesting
    @Nullable
    static String getHostAddress(InetSocketAddress socketAddress) {
        InetAddress address = socketAddress.getAddress();
        if (address instanceof Inet6Address) {
            // Strip the scope from the address since some other classes choke on it.
            // TODO(carl-mastrangelo): Consider adding this back in once issues like
            // https://github.com/google/guava/issues/2587 are fixed.
            try {
                return InetAddress.getByAddress(address.getAddress()).getHostAddress();
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
        } else if (address instanceof Inet4Address) {
            return address.getHostAddress();
        } else {
            assert address == null;
            return null;
        }
    }

    private InetSocketAddress sourceAddress(Channel channel)
    {
        SocketAddress remoteSocketAddr = channel.remoteAddress();
        if (null != remoteSocketAddr && InetSocketAddress.class.isAssignableFrom(remoteSocketAddr.getClass())) {
            InetSocketAddress inetSocketAddress = (InetSocketAddress) remoteSocketAddr;
            if (inetSocketAddress.getAddress() != null) {
                return inetSocketAddress;
            }
        }
        return null;
    }

    private InetSocketAddress localAddress(Channel channel)
    {
        SocketAddress localSocketAddress = channel.localAddress();
        if (null != localSocketAddress && InetSocketAddress.class.isAssignableFrom(localSocketAddress.getClass())) {
            InetSocketAddress inetSocketAddress = (InetSocketAddress) localSocketAddress;
            if (inetSocketAddress.getAddress() != null) {
                return inetSocketAddress;
            }
        }
        return null;
    }
}
