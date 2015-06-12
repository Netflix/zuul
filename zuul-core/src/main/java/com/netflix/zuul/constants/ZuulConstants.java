package com.netflix.zuul.constants;

/*
 * Copyright 2013 Netflix, Inc.
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

/**
 * property constants
 * Date: 5/15/13
 * Time: 2:22 PM
 */
public class ZuulConstants {
    public static final String ZUUL_ARCHAIUS_DYNAMODB_ENABLED = "zuul.archaius.dynamodb.enabled";
    public static final String ZUUL_CASSANDRA_ENABLED = "zuul.cassandra.enabled";
    public static final String ZUUL_NIWS_CLIENTLIST = "zuul.niws.clientlist";
    public static final String DEFAULT_NFASTYANAX_READCONSISTENCY = "default.nfastyanax.readConsistency";
    public static final String DEFAULT_NFASTYANAX_WRITECONSISTENCY = "default.nfastyanax.writeConsistency";
    public static final String DEFAULT_NFASTYANAX_SOCKETTIMEOUT = "default.nfastyanax.socketTimeout";
    public static final String DEFAULT_NFASTYANAX_MAXCONNSPERHOST = "default.nfastyanax.maxConnsPerHost";
    public static final String DEFAULT_NFASTYANAX_MAXTIMEOUTWHENEXHAUSTED = "default.nfastyanax.maxTimeoutWhenExhausted";
    public static final String DEFAULT_NFASTYANAX_MAXFAILOVERCOUNT = "default.nfastyanax.maxFailoverCount";
    public static final String DEFAULT_NFASTYANAX_FAILOVERWAITTIME = "default.nfastyanax.failoverWaitTime";
    public static final String ZUUL_CASSANDRA_KEYSPACE = "zuul.cassandra.keyspace";
    public static final String ZUUL_CASSANDRA_MAXCONNECTIONSPERHOST = "zuul.cassandra.maxConnectionsPerHost";
    public static final String ZUUL_CASSANDRA_HOST = "zuul.cassandra.host";
    public static final String ZUUL_CASSANDRA_PORT = "zuul.cassandra.port";
    public static final String ZUUL_CASSANDRA_CSQL_VERSION = "zuul.cassandra.csqlVersion";
    public static final String ZUUL_CASSANDRA_TARGET_VERSION = "zuul.cassandra.targetVersion";

    public static final String ZUUL_EUREKA = "zuul.eureka.";
    public static final String ZUUL_AUTODETECT_BACKEND_VIPS = "zuul.autodetect-backend-vips";
    public static final String ZUUL_RIBBON_NAMESPACE = "zuul.ribbon.namespace";
    public static final String ZUUL_RIBBON_VIPADDRESS_TEMPLATE = "zuul.ribbon.vipAddress.template";
    public static final String ZUUL_CASSANDRA_CACHE_MAX_SIZE = "zuul.cassandra.cache.max-size";
    public static final String ZUUL_HTTPCLIENT = "zuul.httpClient.";
    public static final String ZUUL_USE_ACTIVE_FILTERS = "zuul.use.active.filters";
    public static final String ZUUL_USE_CANARY_FILTERS = "zuul.use.canary.filters";
    public static final String ZUUL_FILTER_PRE_PATH = "zuul.filter.pre.path";
    public static final String ZUUL_FILTER_POST_PATH = "zuul.filter.post.path";
    public static final String ZUUL_FILTER_ROUTING_PATH = "zuul.filter.routing.path";
    public static final String ZUUL_FILTER_CUSTOM_PATH = "zuul.filter.custom.path";
    public static final String ZUUL_FILTER_ADMIN_ENABLED = "zuul.filter.admin.enabled";
    public static final String ZUUL_FILTER_ADMIN_REDIRECT = "zuul.filter.admin.redirect.path";


    public static final String ZUUL_DEBUG_REQUEST = "zuul.debug.request";
    public static final String ZUUL_DEBUG_PARAMETER = "zuul.debug.parameter";
    public static final String ZUUL_ROUTER_ALT_ROUTE_VIP = "zuul.router.alt.route.vip";
    public static final String ZUUL_ROUTER_ALT_ROUTE_HOST = "zuul.router.alt.route.host";
    public static final String ZUUL_ROUTER_ALT_ROUTE_PERMYRIAD = "zuul.router.alt.route.permyriad";
    public static final String ZUUL_ROUTER_ALT_ROUTE_MAXLIMIT = "zuul.router.alt.route.maxlimit";
    public static final String ZUUL_NIWS_DEFAULTCLIENT = "zuul.niws.defaultClient";
    public static final String ZUUL_DEFAULT_HOST = "zuul.default.host";
    public static final String ZUUL_HOST_SOCKET_TIMEOUT_MILLIS = "zuul.host.socket-timeout-millis";
    public static final String ZUUL_HOST_CONNECT_TIMEOUT_MILLIS = "zuul.host.connect-timeout-millis";
    public static final String ZUUL_INCLUDE_DEBUG_HEADER = "zuul.include-debug-header";
    public static final String ZUUL_INITIAL_STREAM_BUFFER_SIZE = "zuul.initial-stream-buffer-size";
    public static final String ZUUL_SET_CONTENT_LENGTH = "zuul.set-content-length";
    public static final String ZUUL_DEBUGFILTERS_DISABLED = "zuul.debugFilters.disabled";
    public static final String ZUUL_DEBUG_VIP = "zuul.debug.vip";
    public static final String ZUUL_DEBUG_HOST = "zuul.debug.host";

}
