package com.netflix.zuul.groovy;

/**
 * Created by IntelliJ IDEA.
 * User: mcohen
 * Date: 10/27/11
 * Time: 3:03 PM
 * To change this template use File | Settings | File Templates.
 */
public interface IProxyFilter {
    boolean shouldFilter();

    Object run();

}
