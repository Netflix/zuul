package com.netflix.zuul.accesslog;

/**
 * An access log record used by {@link AccessLogPublisher}
 *
 * @author Nitesh Kant
 */
public interface AccessRecord
{
    public String toLogLine();
}
