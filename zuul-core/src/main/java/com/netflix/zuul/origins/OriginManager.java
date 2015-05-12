package com.netflix.zuul.origins;

/**
 * User: michaels@netflix.com
 * Date: 5/11/15
 * Time: 3:15 PM
 */
public interface OriginManager {
    Origin getOrigin(String name);
}
