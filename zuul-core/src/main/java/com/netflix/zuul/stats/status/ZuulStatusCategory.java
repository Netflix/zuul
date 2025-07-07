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

package com.netflix.zuul.stats.status;

import com.google.errorprone.annotations.Immutable;

/**
 * Zuul Status Category
 *
 * As some of the origin servers won't/can't return correct HTTP status codes in responses, we use set an
 * StatusCategory attribute to distinguish the main statuses that we care about from Zuul's perspective.
 *
 * These status categories are split into 2 groups:
 *
 *    SUCCESS | FAILURE
 *
 * each of which can have a narrower definition, eg:
 *
 *    FAILURE_THROTTLED
 *    FAILURE_ORIGIN
 *    etc...
 *
 *  which _should_ also be subdivided with one of:
 *
 *    ORIGIN
 *    CLIENT
 *    LOCAL
 */
@Immutable
public enum ZuulStatusCategory implements StatusCategory {
    SUCCESS(ZuulStatusCategoryGroup.SUCCESS, 1, "Successfully proxied"),
    SUCCESS_NOT_FOUND(
            ZuulStatusCategoryGroup.SUCCESS,
            3,
            "Successfully proxied, origin responded with no resource found"), // This is set on for all 404 responses

    SUCCESS_LOCAL_NOTSET(
            ZuulStatusCategoryGroup.SUCCESS,
            4,
            "Default status"), // This is set on the SessionContext as the default value.
    SUCCESS_LOCAL_NO_ROUTE(ZuulStatusCategoryGroup.SUCCESS, 5, "Unable to determine an origin to handle request"),

    FAILURE_LOCAL(ZuulStatusCategoryGroup.FAILURE, 1, "Failed internally"),
    FAILURE_LOCAL_THROTTLED_ORIGIN_SERVER_MAXCONN(
            ZuulStatusCategoryGroup.FAILURE, 7, "Throttled due to reaching max number of connections to origin"),
    FAILURE_LOCAL_THROTTLED_ORIGIN_CONCURRENCY(
            ZuulStatusCategoryGroup.FAILURE, 8, "Throttled due to reaching concurrency limit to origin"),
    FAILURE_LOCAL_IDLE_TIMEOUT(ZuulStatusCategoryGroup.FAILURE, 9, "Idle timeout due to channel inactivity"),
    FAILURE_LOCAL_HEADER_FIELDS_TOO_LARGE(ZuulStatusCategoryGroup.FAILURE, 5, "Header Fields Too Large"),
    FAILURE_CLIENT_BAD_REQUEST(ZuulStatusCategoryGroup.FAILURE, 12, "Invalid request provided"),
    FAILURE_CLIENT_CANCELLED(ZuulStatusCategoryGroup.FAILURE, 13, "Client abandoned/closed the connection"),
    FAILURE_CLIENT_PIPELINE_REJECT(ZuulStatusCategoryGroup.FAILURE, 17, "Client rejected due to HTTP Pipelining"),
    FAILURE_CLIENT_TIMEOUT(ZuulStatusCategoryGroup.FAILURE, 18, "Timeout reading the client request"),

    FAILURE_ORIGIN(ZuulStatusCategoryGroup.FAILURE, 2, "Origin returned an error status"),
    FAILURE_ORIGIN_READ_TIMEOUT(ZuulStatusCategoryGroup.FAILURE, 3, "Timeout reading the response from origin"),
    FAILURE_ORIGIN_CONNECTIVITY(ZuulStatusCategoryGroup.FAILURE, 4, "Connection to origin failed"),
    FAILURE_ORIGIN_THROTTLED(ZuulStatusCategoryGroup.FAILURE, 6, "Throttled by origin returning 503 status"),
    FAILURE_ORIGIN_NO_SERVERS(ZuulStatusCategoryGroup.FAILURE, 14, "No UP origin servers available in Discovery"),
    FAILURE_ORIGIN_RESET_CONNECTION(
            ZuulStatusCategoryGroup.FAILURE, 15, "Connection reset on an established origin connection"),
    FAILURE_ORIGIN_CLOSE_NOTIFY_CONNECTION(ZuulStatusCategoryGroup.FAILURE, 16, "Connection TLS session shutdown");

    private final StatusCategoryGroup group;
    private final String id;
    private final String reason;

    ZuulStatusCategory(StatusCategoryGroup group, int index, String reason) {
        this.group = group;
        this.id = (group.getId() + "_" + index).intern();
        this.reason = reason;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public StatusCategoryGroup getGroup() {
        return group;
    }

    @Override
    public String getReason() {
        return reason;
    }
}
