package com.netflix.zuul;

/**
 * Created with IntelliJ IDEA.
 * User: mcohen
 * Date: 2/15/13
 * Time: 1:56 PM
 * To change this template use File | Settings | File Templates.
 */
public class ZuulApplicationInfo {
    public static String applicationName;
    public static String stack;

    public static String getApplicationName() {
        return applicationName;
    }

    public static void setApplicationName(String applicationName) {
        ZuulApplicationInfo.applicationName = applicationName;
    }

    public static String getStack() {
        return stack;
    }

    public static void setStack(String stack) {
        ZuulApplicationInfo.stack = stack;
    }
}
