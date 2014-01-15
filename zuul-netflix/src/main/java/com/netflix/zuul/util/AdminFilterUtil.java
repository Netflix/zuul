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
package com.netflix.zuul.util;

import com.netflix.zuul.scriptManager.FilterInfo;
import com.netflix.zuul.scriptManager.FilterScriptManagerServlet;

/**
 * Utility method to build form data for the Admin page for uploading and downloading filters
 * @author Mikey Cohen
 * Date: 6/19/12
 * Time: 10:43 AM
 */
public class AdminFilterUtil {

    public static String getState(FilterInfo filter) {
        String state = "inactive";
        if(filter.isActive())state = "active";
        if(filter.isCanary())state = "canary";
        return state;

    }

    public static String buildDeactivateForm(String filter_id, int revision) {
        if (FilterScriptManagerServlet.adminEnabled.get()) {
            return "<form  method=\"POST\" action=\"scriptmanager?action=DEACTIVATE&filter_id=" + filter_id + "&revision=" + revision + "\" >\n" +
                   "<input type=\"submit\" value=\"deactivate\"/></form>";
        } else {
            return "";
        }
    }

    public static String buildActivateForm(String filter_id, int revision) {
        if (FilterScriptManagerServlet.adminEnabled.get()) {
            return "<form  method=\"POST\" action=\"scriptmanager?action=ACTIVATE&filter_id=" + filter_id + "&revision=" + revision + "\" >\n" +
                   "<input type=\"submit\" value=\"activate\"/></form>";
        } else {
            return "";
        }
    }

    public static String buildCanaryForm(String filter_id, int revision) {
        if (FilterScriptManagerServlet.adminEnabled.get()) {
            return "<form  method=\"POST\" action=\"scriptmanager?action=CANARY&filter_id=" + filter_id + "&revision=" + revision + "\" >\n" +
                   "<input type=\"submit\" value=\"canary\"/></form>";
        } else {
            return "";
        }
    }

    public static String buildDownloadLink(String filter_id, int revision) {
        return "<a href=scriptmanager?action=DOWNLOAD&filter_id=" + filter_id + "&revision=" + revision + ">DOWNLOAD</a>";
    }

}
