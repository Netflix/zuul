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
public enum ZuulStatusCategory implements StatusCategory {
    SUCCESS(ZuulStatusCategoryGroup.SUCCESS, 1),
    SUCCESS_NOT_FOUND(ZuulStatusCategoryGroup.SUCCESS, 3),   // This is set on for all 404 responses

    SUCCESS_LOCAL_NOTSET(ZuulStatusCategoryGroup.SUCCESS, 4),   // This is set on the SessionContext as the default value.
    SUCCESS_LOCAL_NO_ROUTE(ZuulStatusCategoryGroup.SUCCESS, 5),

    FAILURE_LOCAL(ZuulStatusCategoryGroup.FAILURE, 1),
    FAILURE_LOCAL_THROTTLED_ORIGIN_SERVER_MAXCONN(ZuulStatusCategoryGroup.FAILURE, 7),  //NIWS client throttling based on max connections per origin server.
    FAILURE_LOCAL_THROTTLED_ORIGIN_CONCURRENCY(ZuulStatusCategoryGroup.FAILURE, 8), // when zuul throttles for a vip because concurrency is too high.
    FAILURE_LOCAL_IDLE_TIMEOUT(ZuulStatusCategoryGroup.FAILURE, 9),

    FAILURE_CLIENT_CANCELLED(ZuulStatusCategoryGroup.FAILURE, 13),  // client abandoned/closed the connection before origin responded.
    FAILURE_CLIENT_PIPELINE_REJECT(ZuulStatusCategoryGroup.FAILURE, 17),
    FAILURE_CLIENT_TIMEOUT(ZuulStatusCategoryGroup.FAILURE, 18),

    FAILURE_ORIGIN(ZuulStatusCategoryGroup.FAILURE, 2),
    FAILURE_ORIGIN_READ_TIMEOUT(ZuulStatusCategoryGroup.FAILURE, 3),
    FAILURE_ORIGIN_CONNECTIVITY(ZuulStatusCategoryGroup.FAILURE, 4),
    FAILURE_ORIGIN_THROTTLED(ZuulStatusCategoryGroup.FAILURE, 6),   // Throttled by origin by returning 503
    FAILURE_ORIGIN_NO_SERVERS(ZuulStatusCategoryGroup.FAILURE, 14),  // No UP origin servers available in Discovery.
    FAILURE_ORIGIN_RESET_CONNECTION(ZuulStatusCategoryGroup.FAILURE, 15);

    private final StatusCategoryGroup group;
    private final String id;

    ZuulStatusCategory(StatusCategoryGroup group, int index) {
        this.group = group;
        this.id = (group.getId() + "_" + index).intern();
    }

    @Override
    public String getId() {
        return id;
    }
    @Override
    public StatusCategoryGroup getGroup() {
        return group;
    }
}
