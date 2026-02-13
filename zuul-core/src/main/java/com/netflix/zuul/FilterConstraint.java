package com.netflix.zuul;

import com.netflix.zuul.message.ZuulMessage;

/**
 * A filter constraint can be registered on {@link Filter#constraints()} to indicate that a given filter should
 * not be run against a ZuulMessage. FilterConstraint's act as a centralized way to implement logic that would otherwise
 * need to be duplicated across multiple {@link com.netflix.zuul.filters.ShouldFilter#shouldFilter(ZuulMessage)}
 *
 * @author Justin Guerra
 * @since 1/9/26
 */
public interface FilterConstraint {
    boolean isConstrained(ZuulMessage msg);
}
