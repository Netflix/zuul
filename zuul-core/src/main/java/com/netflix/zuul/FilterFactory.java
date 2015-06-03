package com.netflix.zuul;

import com.netflix.zuul.filters.BaseFilter;

/**
 * Interface to provide instances of ZuulFilter from a given class.
 */
public interface FilterFactory {
    
    /**
     * Returns an instance of the specified class.
     * 
     * @param clazz the Class to instantiate
     * @return an instance of ZuulFilter
     * @throws Exception if an error occurs
     */
    public BaseFilter newInstance(Class clazz) throws Exception;
}
