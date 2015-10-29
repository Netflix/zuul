/**
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul;


import com.netflix.config.ConfigurationManager;
import com.netflix.config.DeploymentContext;
import com.netflix.zuul.FilterFileManager.FilterFileManagerConfig;
import com.netflix.zuul.FilterProcessor.BasicFilterUsageNotifier;
import com.netflix.zuul.context.RxNettySessionContextFactory;
import com.netflix.zuul.context.SampleSessionContextDecorator;
import com.netflix.zuul.context.SessionCleaner;
import com.netflix.zuul.context.SessionContextDecorator;
import com.netflix.zuul.context.SessionContextFactory;
import com.netflix.zuul.origins.OriginManager;
import com.netflix.zuul.rxnetty.RxNettyOriginManager;
import com.netflix.zuul.rxnetty.StaticHostSourceFactory;
import com.netflix.zuul.rxnetty.ZuulRequestHandler;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.logging.LogLevel;
import io.reactivex.netty.protocol.http.server.HttpServer;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.net.SocketAddress;

/**
 * User: Mike Smith
 * Date: 3/15/15
 * Time: 5:22 PM
 */
public class NettySampleStartServer
{

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final static String DEFAULT_APP_NAME = "zuul";
    private final ZuulRequestHandler requestHandler;

    public NettySampleStartServer(SocketAddress origin) throws Exception {

        loadProperties();

        OriginManager originManager = new RxNettyOriginManager(new StaticHostSourceFactory(origin));
        FilterLoader filterLoader = new FilterLoader();
        FilterProcessor filterProcessor = new FilterProcessor(filterLoader, new BasicFilterUsageNotifier());
        FilterFileManagerConfig config =
                new FilterFileManagerConfig(new String[] {
                        "/Users/nkant/work/workspace/github/zuul-revert/zuul-netty-sample/src/main/filters/endpoint",
                        "/Users/nkant/work/workspace/github/zuul-revert/zuul-netty-sample/src/main/filters/inbound",
                        "/Users/nkant/work/workspace/github/zuul-revert/zuul-netty-sample/src/main/filters/outbound"
                }, new String[0], 60);
        FilterFileManager filterFileManager = new FilterFileManager(config, filterLoader);
        filterFileManager.init();

        SessionContextFactory<HttpServerRequest<ByteBuf>, HttpServerResponse<ByteBuf>> sessionCtxFactory =
                new RxNettySessionContextFactory();

        SessionContextDecorator ctxDecorator = new SampleSessionContextDecorator(originManager);

        SessionCleaner cleaner = context -> Observable.empty();

        ZuulHttpProcessor<HttpServerRequest<ByteBuf>, HttpServerResponse<ByteBuf>> processor =
                new ZuulHttpProcessor<>(filterProcessor, sessionCtxFactory, ctxDecorator, null, filterFileManager,
                                        cleaner);

        requestHandler = new ZuulRequestHandler(processor);
    }

    public void startAndAwaitShutdown(int port) {
        HttpServer.newServer(port)
                  .enableWireLogging(LogLevel.DEBUG)
                  .start(requestHandler)
                  .awaitShutdown();
    }

    private void loadProperties() {
        DeploymentContext deploymentContext = ConfigurationManager.getDeploymentContext();
        if (deploymentContext.getApplicationId() == null) {
            deploymentContext.setApplicationId(DEFAULT_APP_NAME);
        }

        String infoStr = String.format("env=%s, region=%s, appId=%s, stack=%s",
                                       deploymentContext.getDeploymentEnvironment(),
                                       deploymentContext.getDeploymentRegion(),
                                       deploymentContext.getApplicationId(),
                                       deploymentContext.getDeploymentStack());

        logger.info("Using deployment context: {} \n", infoStr);

        try {
            ConfigurationManager.loadCascadedPropertiesFromResources(deploymentContext.getApplicationId());
        } catch (Exception e) {
            logger.error(String.format("Failed to load properties file: %s.", infoStr), e);
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {

        SocketAddress origin = HttpServer.newServer(7002)
                                         .start((request, response) -> response.setStatus(HttpResponseStatus.OK)
                                                                               .writeString(Observable.just("Hello world!")))
                                         .getServerAddress();

        NettySampleStartServer server = new NettySampleStartServer(origin);
        server.startAndAwaitShutdown(7001);

    }
}