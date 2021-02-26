package com.netflix.zuul.resolver;

/**
 * @author Argha C
 * @since 2/25/21
 *
 * Resolves a key to a discovery result type.
 */
public interface Resolver<T> {

    //TODO(argha-c) Param needs to be typed, once the ribbon LB lookup API is figured out.
    T resolve(Object key);
}
