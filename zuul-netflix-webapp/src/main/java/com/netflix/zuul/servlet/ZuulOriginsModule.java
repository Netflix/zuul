package com.netflix.zuul.servlet;

import com.google.inject.AbstractModule;
import com.netflix.zuul.origins.OriginManager;
import com.netflix.zuul.ribbon.RibbonOriginManager;

/**
 * User: michaels@netflix.com
 * Date: 5/13/15
 * Time: 11:41 AM
 */
public class ZuulOriginsModule extends AbstractModule
{
    @Override
    protected void configure()
    {
        bind(OriginManager.class).to(RibbonOriginManager.class);
    }
}
