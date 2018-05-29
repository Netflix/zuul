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

/**
 * Common Context Keys
 *
 * Author: Arthur Gonigberg
 * Date: November 21, 2017
 */
public class CommonContextKeys {

    public static final String STATUS_CATGEORY = "status_category";
    public static final String ORIGIN_STATUS_CATEGORY = "origin_status_category";
    public static final String ORIGIN_STATUS = "origin_status";
    public static final String REQUEST_ATTEMPTS = "request_attempts";

    public static final String REST_CLIENT_CONFIG = "rest_client_config";
    public static final String REST_EXECUTION_CONTEXT = "rest_exec_ctx";

    public static final String ZUUL_ENDPOINT = "_zuul_endpoint";
    public static final String ZUUL_FILTER_CHAIN = "_zuul_filter_chain";
    public static final String ZUUL_ORIGIN_ATTEMPT_IPADDR_MAP_KEY = "_zuul_origin_attempt_ipaddr_map";
    public static final String ZUUL_ORIGIN_REQUEST_URI = "_zuul_origin_request_uri";
    public static final String ORIGIN_MANAGER = "origin_manager";
    public static final String ROUTING_LOG = "routing_log";
    public static final String USE_FULL_VIP_NAME = "use_full_vip_name";
    public static final String ACTUAL_VIP = "origin_vip_actual";
    public static final String ORIGIN_VIP_SECURE = "origin_vip_secure";

    public static final String SSL_HANDSHAKE_INFO = "ssl_handshake_info";

    public static final String GZIPPER = "gzipper";
    public static final String OVERRIDE_GZIP_REQUESTED = "overrideGzipRequested";

    /* Netty-specific keys */
    public static final String IS_NETTY_BUILD = "_is_netty_build";
    public static final String NETTY_HTTP_REQUEST = "_netty_http_request";
    public static final String NETTY_SERVER_CHANNEL_HANDLER_CONTEXT = "_netty_server_channel_handler_context";
    public static final String REQ_BODY_DCS = "_request_body_dcs";
    public static final String RESP_BODY_DCS = "_response_body_dcs";

    public static final String REQ_BODY_SIZE_PROVIDER = "request_body_size";
    public static final String RESP_BODY_SIZE_PROVIDER = "response_body_size";

    public static final String PASSPORT = "_passport";
}
