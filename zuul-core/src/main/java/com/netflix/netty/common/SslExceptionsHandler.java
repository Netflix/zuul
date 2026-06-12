/*
 * Copyright 2023 Netflix, Inc.
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

import com.netflix.spectator.api.Registry;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import javax.net.ssl.SSLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Swallow specific SSL related exceptions to avoid propagating deep stack traces up the pipeline.
 *
 * @author Argha C
 * @since 4/17/23
 */
@Sharable
public class SslExceptionsHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(SslExceptionsHandler.class);
    private final Registry registry;

    public SslExceptionsHandler(Registry registry) {
        this.registry = registry;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // In certain cases, depending on the client, these stack traces can get very deep.
        // We intentionally avoid propagating this up the pipeline, to avoid verbose disk logging.
        SSLException sslException = findSslException(cause);
        if (sslException != null) {
            logger.debug("SSL exception swallowed on channel {}", ctx.channel(), cause);
            registry.counter(
                            "server.ssl.exception.swallowed",
                            "cause",
                            sslException.getClass().getSimpleName())
                    .increment();
        } else {
            super.exceptionCaught(ctx, cause);
        }
    }

    // SSL errors raised during decode arrive wrapped in a DecoderException,
    // so the SSLException is not the top-level cause
    private static SSLException findSslException(Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof SSLException sslException) {
                return sslException;
            }

            current = current.getCause();
        }

        return null;
    }
}
