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

package com.netflix.zuul.context;

import com.netflix.client.config.IClientConfig;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.niws.RequestAttempts;
import com.netflix.zuul.stats.status.StatusCategory;
import java.net.InetAddress;
import java.util.Map;

/**
 * Common Context Keys
 *
 * Author: Arthur Gonigberg
 * Date: November 21, 2017
 */
public class CommonContextKeys {

    public static final SessionContext.Key<StatusCategory> STATUS_CATGEORY =
            SessionContext.newKey("status_category");
    public static final SessionContext.Key<StatusCategory> ORIGIN_STATUS_CATEGORY =
            SessionContext.newKey("origin_status_category");
    public static final SessionContext.Key<Integer>  ORIGIN_STATUS = SessionContext.newKey("origin_status");
    public static final SessionContext.Key<RequestAttempts> REQUEST_ATTEMPTS =
            SessionContext.newKey("request_attempts");

    public static final SessionContext.Key<IClientConfig> REST_CLIENT_CONFIG =
            SessionContext.newKey("rest_client_config");

    public static final SessionContext.Key<ZuulFilter<HttpRequestMessage, HttpResponseMessage>> ZUUL_ENDPOINT =
            SessionContext.newKey("_zuul_endpoint");
    public static final SessionContext.Key<Map<Integer, InetAddress>> ZUUL_ORIGIN_CHOSEN_HOST_ADDR_MAP_KEY =
            SessionContext.newKey("_zuul_origin_chosen_host_addr_map");
    public static final String ZUUL_ORIGIN_REQUEST_URI = "_zuul_origin_request_uri";
    public static final String ORIGIN_CHANNEL = "_origin_channel";
    public static final String ORIGIN_MANAGER = "origin_manager";
    public static final String ROUTING_LOG = "routing_log";
    public static final String USE_FULL_VIP_NAME = "use_full_vip_name";
    public static final String ACTUAL_VIP = "origin_vip_actual";
    public static final String ORIGIN_VIP_SECURE = "origin_vip_secure";

    /**
     * The original client destination address Zuul by a proxy running Proxy Protocol.
     * Will only be set if both Zuul and the connected proxy are both using set to use Proxy Protocol.
     */
    public static final String PROXY_PROTOCOL_DESTINATION_ADDRESS = "proxy_protocol_destination_address";

    public static final String SSL_HANDSHAKE_INFO = "ssl_handshake_info";

    public static final String GZIPPER = "gzipper";
    public static final String OVERRIDE_GZIP_REQUESTED = "overrideGzipRequested";

    /* Netty-specific keys */
    public static final String NETTY_HTTP_REQUEST = "_netty_http_request";
    public static final String NETTY_SERVER_CHANNEL_HANDLER_CONTEXT = "_netty_server_channel_handler_context";
    public static final String REQ_BODY_DCS = "_request_body_dcs";
    public static final String RESP_BODY_DCS = "_response_body_dcs";

    public static final String REQ_BODY_SIZE_PROVIDER = "request_body_size";
    public static final String RESP_BODY_SIZE_PROVIDER = "response_body_size";

    public static final String PASSPORT = "_passport";
    public static final String ZUUL_USE_DECODED_URI = "zuul_use_decoded_uri";
}
