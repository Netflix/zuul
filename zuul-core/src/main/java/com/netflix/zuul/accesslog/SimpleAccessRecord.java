package com.netflix.zuul.accesslog;

import com.netflix.config.DynamicStringListProperty;
import io.reactivex.netty.protocol.http.server.HttpRequestHeaders;
import io.reactivex.netty.protocol.http.server.HttpResponseHeaders;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

/**
 * TODO - Allow logging arbitrary attributes from RequestContext.
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
    private final String path;
    private final String query;
    private final long durationNs;
    private final int responseBodySize;
    private final HttpRequestHeaders requestHeaders;
    private final HttpResponseHeaders responseHeaders;

    public SimpleAccessRecord(LocalDateTime timestamp, int statusCode, String httpMethod, String path, String query,
                              long durationNs, int responseBodySize,
                              HttpRequestHeaders requestHeaders, HttpResponseHeaders responseHeaders) {
        this.timestamp = timestamp;
        this.statusCode = statusCode;
        this.httpMethod = httpMethod;
        this.path = path;
        this.query = query;
        this.durationNs = durationNs;
        this.responseBodySize = responseBodySize;
        this.requestHeaders = requestHeaders;
        this.responseHeaders = responseHeaders;
    }

    @Override
    public String toLogLine() {
        StringBuilder sb = new StringBuilder();

        sb.append(timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .append(DELIM).append(statusCode)
                .append(DELIM).append(httpMethod)
                .append(DELIM).append(path)
                .append(DELIM).append(query == null ? "-" : query)
                .append(DELIM).append(durationNs / 1000) // Converting duration to microseconds.
                .append(DELIM).append(responseBodySize);

        includeMatchingHeaders(sb, LOG_RESP_HEADERS.get(), name -> responseHeaders.getHeader(name));
        includeMatchingHeaders(sb, LOG_REQ_HEADERS.get(), name -> requestHeaders.getHeader(name));

        return sb.toString();
    }

    void includeMatchingHeaders(StringBuilder builder, List<String> requiredHeaders, Function<String, String> getHeader)
    {
        for (String headerName : requiredHeaders) {
            String value = getHeader.apply(headerName);
            if (value == null) value = "-";
            builder.append(DELIM).append('\"').append(value).append('\"');
        }
    }
}
