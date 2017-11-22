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

package com.netflix.zuul.netty.server.http2;

import com.netflix.config.DynamicBooleanProperty;
import com.netflix.zuul.netty.ssl.SslContextFactory;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import javax.net.ssl.SSLException;

public class Http2Configuration {

    private static final DynamicBooleanProperty HTTP2_DISABLED =
            new DynamicBooleanProperty("zuul.server.http2.disabled", false);


    public static SslContext configureSSL(SslContextFactory sslContextFactory, int port) {
        SslContextBuilder builder = sslContextFactory.createBuilderForServer();

        String[] supportedProtocol;
        if (HTTP2_DISABLED.get()) {
            supportedProtocol = new String[]{ApplicationProtocolNames.HTTP_1_1};
        }
        else {
            supportedProtocol = new String[]{ApplicationProtocolNames.HTTP_2,
                    ApplicationProtocolNames.HTTP_1_1};
        }

        ApplicationProtocolConfig apn = new ApplicationProtocolConfig(
                ApplicationProtocolConfig.Protocol.ALPN,
                // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
                ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
                ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                supportedProtocol);

        final SslContext sslContext;
        try {
            sslContext = builder
                    .applicationProtocolConfig(apn)
                    .build();
        }
        catch (SSLException e) {
            throw new RuntimeException("Error configuring SslContext with ALPN!", e);
        }

        // Enable TLS Session Tickets support.
        sslContextFactory.enableSessionTickets(sslContext);

        // Setup metrics tracking the OpenSSL stats.
        sslContextFactory.configureOpenSslStatsMetrics(sslContext, Integer.toString(port));

        return sslContext;
    }
}
