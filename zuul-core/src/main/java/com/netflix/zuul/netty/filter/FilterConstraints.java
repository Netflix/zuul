package com.netflix.zuul.netty.filter;

import com.netflix.zuul.FilterConstraint;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.message.ZuulMessage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;

/**
 * @author Justin Guerra
 * @since 1/9/26
 */
@NullMarked
public class FilterConstraints {

    @SuppressWarnings("unchecked")
    private static final Class<? extends FilterConstraint>[] NO_CONSTRAINTS = new Class[0];

    private final Map<Class<? extends FilterConstraint>, FilterConstraint> lookup;
    private final Map<String, Class<? extends FilterConstraint>[]> filterConstraints;

    public FilterConstraints(List<FilterConstraint> constraints) {
        this.lookup = constraints.stream().collect(Collectors.toUnmodifiableMap(FilterConstraint::getClass, c -> c));
        this.filterConstraints = new ConcurrentHashMap<>();
    }

    /**
     * Checks if any {@link FilterConstraint}'s are active for the given msg
     */
    public boolean isConstrained(ZuulMessage msg, ZuulFilter<?, ?> filter) {
        Class<? extends FilterConstraint>[] constraints =
                filterConstraints.computeIfAbsent(filter.getClass().getName(), f -> {
                    Class<? extends FilterConstraint>[] filterConstraints = filter.constraints();
                    return filterConstraints != null ? filterConstraints : NO_CONSTRAINTS;
                });

        for (Class<? extends FilterConstraint> constraint : constraints) {
            FilterConstraint filterConstraint = lookup.get(constraint);
            if (filterConstraint != null && filterConstraint.isConstrained(msg)) {
                return true;
            }
        }

        return false;
    }
}
