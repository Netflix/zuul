package com.netflix.zuul.filters;

/**
 * User: michaels@netflix.com
 * Date: 6/23/15
 * Time: 10:44 AM
 */
public enum FilterPriority
{
    LOW(0), NORMAL(10), HIGH(20);

    private final int code;

    private FilterPriority(int code)
    {
        this.code = code;
    }

    public int getCode()
    {
        return code;
    }
}
