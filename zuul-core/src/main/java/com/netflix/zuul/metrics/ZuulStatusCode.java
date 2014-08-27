/**
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.zuul.metrics;

import com.netflix.numerus.NumerusRollingNumberEvent;

public enum ZuulStatusCode implements NumerusRollingNumberEvent {
    OneXX("1xx"), TwoXX("2xx"), ThreeXX("3xx"), FourXX("4xx"), FiveXX("5xx"), INIT("???");

    private final String asString;

    private ZuulStatusCode(String s) {
        asString = s;
    }

    @Override
    public boolean isCounter() {
        return true;
    }

    @Override
    public boolean isMaxUpdater() {
        return false;
    }

    @Override
    public NumerusRollingNumberEvent[] getValues() {
        return values();
    }

    public String str() {
        return asString;
    }
}
