package com.netflix.zuul.filters;

/**
 * User: Mike Smith
 * Date: 11/13/15
 * Time: 7:50 PM
 */
public enum FilterType
{
    INBOUND("in"), ENDPOINT("end"), OUTBOUND("out");

    private final String shortName;

    private FilterType(String shortName) {
        this.shortName = shortName;
    }

    @Override
    public String toString()
    {
        return shortName;
    }

    public static FilterType parse(String str)
    {
        str = str.toLowerCase();
        switch (str) {
        case "in":
            return INBOUND;
        case "out":
            return OUTBOUND;
        case "end":
            return ENDPOINT;
        default:
            throw new IllegalArgumentException("Unknown filter type! type=" + String.valueOf(str));
        }
    }
}
