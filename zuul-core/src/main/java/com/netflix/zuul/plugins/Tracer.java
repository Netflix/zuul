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
package com.netflix.zuul.plugins;

import com.netflix.spectator.api.Spectator;
import com.netflix.zuul.monitoring.TracerFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

/**
 * Plugin to hook up Servo Tracers
 *
 * @author Mikey Cohen
 *         Date: 4/10/13
 *         Time: 4:51 PM
 */
public class Tracer extends TracerFactory {

    @Override

    public com.netflix.zuul.monitoring.Tracer startMicroTracer(String name) {
        return new SpectatorTracer(name);
    }

    class SpectatorTracer implements com.netflix.zuul.monitoring.Tracer {

        private String name;
        private final long start;

        private SpectatorTracer(String name) {
            this.name = name;
            start = System.nanoTime();
        }

        @Override
        public void stopAndLog() {
            Spectator.globalRegistry().timer(name, "hostname", getHostName(), "ip", getIp())
                    .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }

        @Override
        public void setName(String name) {
            this.name = name;
        }
    }

    private static String getHostName() {
        return (loadAddress() != null) ? loadAddress().getHostName() : "unkownHost";
    }

    private static String getIp() {
        return (loadAddress() != null) ? loadAddress().getHostAddress() : "unknownHost";
    }

    private static InetAddress loadAddress() {
        try {
            return InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
