package com.netflix.zuul.util;

import com.netflix.zuul.scriptManager.FilterInfo;
import com.sun.jersey.core.util.StringIgnoreCaseKeyComparator;

/**
 * Created with IntelliJ IDEA.
 * User: mcohen
 * Date: 6/19/12
 * Time: 10:43 AM
 * To change this template use File | Settings | File Templates.
 */
public class AdminFilterUtil {

    public static String getState(FilterInfo filter) {
        String state = "inactive";
        if(filter.isActive())state = "active";
        if(filter.isCanary())state = "canary";
        return state;

    }

    public static String buildDeactivateForm(String filter_id, int revision) {
        return "<form  method=\"POST\" action=\"scriptmanager?action=DEACTIVATE&filter_id=" + filter_id + "&revision=" + revision + "\" >\n" +
                "<input type=\"submit\" value=\"deactivate\"/></form>";
    }

    public static String buildActivateForm(String filter_id, int revision) {
        return "<form  method=\"POST\" action=\"scriptmanager?action=ACTIVATE&filter_id=" + filter_id + "&revision=" + revision + "\" >\n" +
                "<input type=\"submit\" value=\"activate\"/></form>";
    }

    public static String buildCanaryForm(String filter_id, int revision) {
        return "<form  method=\"POST\" action=\"scriptmanager?action=CANARY&filter_id=" + filter_id + "&revision=" + revision + "\" >\n" +
                "<input type=\"submit\" value=\"canary\"/></form>";
    }

    public static String buildDownloadLink(String filter_id, int revision) {
        return "<a href=scriptmanager?action=DOWNLOAD&filter_id=" + filter_id + "&revision=" + revision + ">DOWNLOAD</a>";
    }

}
