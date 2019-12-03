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

package com.netflix.zuul.netty.ssl;

import com.netflix.config.DynamicBooleanProperty;
import com.netflix.netty.common.ssl.ServerSslConfig;
import com.netflix.spectator.api.Registry;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client Ssl Context Factory
 *
 * Author: Arthur Gonigberg
 * Date: May 14, 2018
 */
public final class ClientSslContextFactory extends BaseSslContextFactory {

    private static final DynamicBooleanProperty ENABLE_CLIENT_TLS13 =
            new DynamicBooleanProperty("com.netflix.zuul.netty.ssl.enable_tls13", false);

    private static final Logger log = LoggerFactory.getLogger(ClientSslContextFactory.class);

    private static final ServerSslConfig DEFAULT_CONFIG = new ServerSslConfig(
            maybeAddTls13(ENABLE_CLIENT_TLS13.get(), "TLSv1.2"),
            ServerSslConfig.getDefaultCiphers(),
            null,
            null
    );

    public ClientSslContextFactory(Registry spectatorRegistry) {
        super(spectatorRegistry, DEFAULT_CONFIG);
    }


    public ClientSslContextFactory(Registry spectatorRegistry, ServerSslConfig serverSslConfig) {
        super(spectatorRegistry, serverSslConfig);
    }

    public SslContext getClientSslContext() {
        try {
            return SslContextBuilder
                    .forClient()
                    .sslProvider(chooseSslProvider())
                    .ciphers(getCiphers(), getCiphersFilter())
                    .protocols(getProtocols())
                    .build();
        }
        catch (Exception e) {
            log.error("Error loading SslContext client request.", e);
            throw new RuntimeException("Error configuring SslContext for client request!", e);
        }
    }

    static String[] maybeAddTls13(boolean enableTls13, String ... defaultProtocols) {
        if (enableTls13) {
            String[] protocols = new String[defaultProtocols.length + 1];
            System.arraycopy(defaultProtocols, 0, protocols, 1, defaultProtocols.length);
            protocols[0] = "TLSv1.3";
            return protocols;
        } else {
            return defaultProtocols;
        }
    }
}
