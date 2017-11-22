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

package io.netty.handler.ssl;

import com.netflix.zuul.netty.ChannelUtils;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import java.util.concurrent.Executor;

/**
 * User: michaels@netflix.com
 * Date: 5/18/17
 * Time: 11:33 AM
 */
public class HackedSslHandler extends SslHandler
{
    private static final Logger LOG = LoggerFactory.getLogger(HackedSslHandler.class);

    public HackedSslHandler(SSLEngine engine)
    {
        super(engine);
    }

    public HackedSslHandler(SSLEngine engine, boolean startTls)
    {
        super(engine, startTls);
    }

    public HackedSslHandler(SSLEngine engine, Executor delegatedTaskExecutor)
    {
        super(engine, delegatedTaskExecutor);
    }

    public HackedSslHandler(SSLEngine engine, boolean startTls, Executor delegatedTaskExecutor)
    {
        super(engine, startTls, delegatedTaskExecutor);
    }

    // Override this so we can add some logging.
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception
    {
        SSLSession session = engine().getSession();

        LOG.warn("SSL Channel Inactive. id = " + String.valueOf(session.getId())
                + ", protocol = " + String.valueOf(session.getProtocol())
                + ", ciphersuite = " + String.valueOf(session.getCipherSuite())
                + ", " + ChannelUtils.channelInfoForLogging(ctx.channel())
                , new RuntimeException("SSL Channel Inactive")
                );

        super.channelInactive(ctx);
    }
}
