package com.netflix.zuul.accesslog;

import com.netflix.config.DynamicStringListProperty;
import com.netflix.zuul.context.Headers;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * TODO - Allow logging arbitrary attributes from SessionContext.
 *
 * @author Nitesh Kant
 * @author Mike Smith
 */
public class SimpleAccessRecord implements AccessRecord {

    private final static char DELIM = '\t';

    private final static DynamicStringListProperty LOG_REQ_HEADERS =
            new DynamicStringListProperty("zuul.access.log.requestheaders", "host,x-forwarded-for");
    private final static DynamicStringListProperty LOG_RESP_HEADERS =
            new DynamicStringListProperty("zuul.access.log.responseheaders", "server,via,content-type");


    private final LocalDateTime timestamp;
    private final int statusCode;
    private final String httpMethod;
    private final String uri;
    private final long durationNs;
    private final int responseBodySize;
    private final Headers requestHeaders;
    private final Headers responseHeaders;

    public SimpleAccessRecord(LocalDateTime timestamp, int statusCode, String httpMethod, String uri,
                              long durationNs, int responseBodySize,
                              Headers requestHeaders, Headers responseHeaders) {
        this.timestamp = timestamp;
        this.statusCode = statusCode;
        this.httpMethod = httpMethod;
        this.uri = uri;
        this.durationNs = durationNs;
        this.responseBodySize = responseBodySize;
        this.requestHeaders = requestHeaders;
        this.responseHeaders = responseHeaders;
    }

    @Override
    public String toLogLine() {
        StringBuilder sb = new StringBuilder();

        sb.append(timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .append(DELIM).append(httpMethod)
                .append(DELIM).append(uri)
                .append(DELIM).append(statusCode)
                .append(DELIM).append(durationNs / 1000) // Converting duration to microseconds.
                .append(DELIM).append(responseBodySize);

        includeMatchingHeaders(sb, LOG_RESP_HEADERS.get(), responseHeaders);
        includeMatchingHeaders(sb, LOG_REQ_HEADERS.get(), requestHeaders);

        return sb.toString();
    }

    void includeMatchingHeaders(StringBuilder builder, List<String> requiredHeaders, Headers headers)
    {
        for (String headerName : requiredHeaders) {
            for (String value : headers.get(headerName)) {
                if (value == null) value = "-";
                builder.append(DELIM).append('\"').append(value).append('\"');
            }
        }
    }
}
