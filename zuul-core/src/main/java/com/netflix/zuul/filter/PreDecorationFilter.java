package com.netflix.zuul.filter;

import com.netflix.zuul.lifecycle.EdgeRequestContext;
import com.netflix.zuul.lifecycle.EgressRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstraction over the filter interfaces for the common case of a
 * Pre Filter that does no IO, and just decorates the request.
 *
 * User: michaels
 * Date: 2/24/15
 * Time: 10:00 AM
 */
public abstract class PreDecorationFilter
        extends FilterComputation<EgressRequest<EdgeRequestContext>>
        implements com.netflix.zuul.filter.PreFilter<EdgeRequestContext>
{
    protected final Logger LOG = LoggerFactory.getLogger("zuul.filter." + this.getClass().getSimpleName());

    public abstract void apply(EdgeRequestContext ctx);

    @Override
    public EgressRequest<EdgeRequestContext> apply(EgressRequest<EdgeRequestContext> input)
    {
        this.apply(input.get());
        return input;
    }
}
