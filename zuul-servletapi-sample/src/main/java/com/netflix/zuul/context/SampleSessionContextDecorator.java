package com.netflix.zuul.context;

import com.netflix.zuul.origins.OriginManager;

import javax.inject.Inject;

/**
 * User: michaels@netflix.com
 * Date: 5/11/15
 * Time: 5:17 PM
 */
public class SampleSessionContextDecorator implements SessionContextDecorator
{
    private final OriginManager originManager;

    @Inject
    public SampleSessionContextDecorator(OriginManager originManager) {
        this.originManager = originManager;
    }

    @Override
    public SessionContext decorate(SessionContext ctx)
    {
        // Add the configured OriginManager to context for use in route filter.
        ctx.getHelpers().put("origin_manager", originManager);

        return ctx;
    }
}
