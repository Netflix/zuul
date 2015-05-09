package com.netflix.zuul;

import com.netflix.zuul.filters.BaseFilter;

/**
 * Default factory for creating instances of ZuulFilter. 
 */
public class DefaultFilterFactory implements FilterFactory {

    /**
     * Returns a new implementation of ZuulFilter as specified by the provided 
     * Class. The Class is instantiated using its nullary constructor.
     * 
     * @param clazz the Class to instantiate
     * @return A new instance of ZuulFilter
     */
    @Override
    public BaseFilter newInstance(Class clazz) throws InstantiationException, IllegalAccessException {
        return (BaseFilter) clazz.newInstance();
    }

}
