package com.netflix.zuul.filter;

import com.netflix.zuul.lifecycle.EdgeRequestContext;
import com.netflix.zuul.lifecycle.EgressResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstraction over the filter interfaces for the common case of a
 * Post Filter that does no IO, and just decorates the response.
 *
 * User: michaels
 * Date: 2/24/15
 * Time: 10:00 AM
 */
public abstract class PostDecorationFilter
        extends FilterComputation<EgressResponse<EdgeRequestContext>>
        implements PostFilter<EdgeRequestContext>
{
    protected final Logger LOG = LoggerFactory.getLogger("zuul.filter." + this.getClass().getSimpleName());

    public abstract void apply(EdgeRequestContext ctx);

    @Override
    public EgressResponse<EdgeRequestContext> apply(EgressResponse<EdgeRequestContext> output)
    {
        this.apply(output.get());
        return output;
    }
}
