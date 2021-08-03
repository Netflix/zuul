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

import com.netflix.zuul.context.CommonContextKeys;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User: michaels@netflix.com
 * Date: 6/9/15
 * Time: 2:48 PM
 */
public class StatusCategoryUtils {
    private static final Logger LOG = LoggerFactory.getLogger(StatusCategoryUtils.class);

    public static StatusCategory getStatusCategory(ZuulMessage msg) {
        return getStatusCategory(msg.getContext());
    }

    @Nullable
    public static StatusCategory getStatusCategory(SessionContext ctx) {
        return ctx.get(CommonContextKeys.STATUS_CATGEORY);
    }

    public static void setStatusCategory(SessionContext ctx, StatusCategory statusCategory) {
        ctx.put(CommonContextKeys.STATUS_CATGEORY, statusCategory);
    }

    @Nullable
    public static StatusCategory getOriginStatusCategory(SessionContext ctx) {
        return ctx.get(CommonContextKeys.ORIGIN_STATUS_CATEGORY);
    }

    public static boolean isResponseHttpErrorStatus(HttpResponseMessage response) {
        boolean isHttpError = false;
        if (response != null) {
            int status = response.getStatus();
            isHttpError = isResponseHttpErrorStatus(status);
        }
        return isHttpError;
    }

    public static boolean isResponseHttpErrorStatus(int status) {
        return (status < 100 || status >= 500);
    }

    public static void storeStatusCategoryIfNotAlreadyFailure(
            final SessionContext context, final StatusCategory statusCategory) {
        if (statusCategory != null) {
            final StatusCategory nfs = context.get(CommonContextKeys.STATUS_CATGEORY);
            if (nfs == null || nfs.getGroup().getId() == ZuulStatusCategoryGroup.SUCCESS.getId()) {
                context.put(CommonContextKeys.STATUS_CATGEORY, statusCategory);
            }
        }
    }
}
