package com.netflix.zuul.util;

/**
 * User: michaels@netflix.com
 * Date: 6/8/15
 * Time: 11:50 AM
 */
public class ProxyUtils
{
    public static boolean isValidRequestHeader(String headerName)
    {
        switch (headerName.toLowerCase()) {
        case "connection":
        case "content-length":
        case "transfer-encoding":
            return false;
        default:
            return true;
        }
    }

    public static boolean isValidResponseHeader(String headerName)
    {
        switch (headerName.toLowerCase()) {
        case "connection":
        case "keep-alive":
        case "content-length":
        case "server":
        case "transfer-encoding":
            return false;
        default:
            return true;
        }
    }
}
