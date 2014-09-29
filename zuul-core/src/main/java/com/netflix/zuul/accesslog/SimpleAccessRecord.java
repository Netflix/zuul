package com.netflix.zuul.accesslog;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author Nitesh Kant
 */
public class SimpleAccessRecord implements AccessRecord {

    private final LocalDateTime timestamp;
    private final int statusCode;
    private final String httpMethod;
    private final String path;

    public SimpleAccessRecord(LocalDateTime timestamp, int statusCode, String httpMethod, String path) {
        this.timestamp = timestamp;
        this.statusCode = statusCode;
        this.httpMethod = httpMethod;
        this.path = path;
    }

    @Override
    public String toLogLine() {
        return timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + " " + statusCode + " " + httpMethod + " " + path;
    }
}
