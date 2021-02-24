package com.netflix.zuul.listeners;

import java.util.List;

/**
 * @author Argha C
 * @since 2/24/21
 */
public interface ResolverListener<T> {

    void onChange(List<T> removedSet);

}
