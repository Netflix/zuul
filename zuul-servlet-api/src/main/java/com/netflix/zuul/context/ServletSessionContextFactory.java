package com.netflix.zuul.context;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Enumeration;

/**
 * User: michaels@netflix.com
 * Date: 4/29/15
 * Time: 11:25 AM
 */
public class ServletSessionContextFactory implements SessionContextFactory<HttpServletRequest>
{
    private static final Logger LOG = LoggerFactory.getLogger(ServletSessionContextFactory.class);

    @Override
    public SessionContext create(HttpServletRequest servletRequest)
    {
        // Parse the headers.
        Headers reqHeaders = new Headers();
        Enumeration headerNames = servletRequest.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = (String) headerNames.nextElement();
            Enumeration values = servletRequest.getHeaders(name);
            while (values.hasMoreElements()) {
                String value = (String) values.nextElement();
                reqHeaders.add(name, value);
            }
        }

        // Parse the url query parameters.
        HttpQueryParams queryParams = HttpQueryParams.parse(servletRequest.getQueryString());

        // Build the request object.
        HttpRequestMessage request = new HttpRequestMessage(servletRequest.getProtocol(), servletRequest.getMethod(), servletRequest.getRequestURI(), queryParams, reqHeaders, servletRequest.getRemoteAddr(), servletRequest.getScheme());

        // Buffer the request body into a byte array.
        request.setBody(bufferBody(servletRequest));

        // Create an empty response object.
        HttpResponseMessage response = new HttpResponseMessage(200);

        return new SessionContext(request, response);
    }

    private byte[] bufferBody(HttpServletRequest servletRequest)
    {
        byte[] body = null;
        try {
            body = IOUtils.toByteArray(servletRequest.getInputStream());
        }
        catch (SocketTimeoutException e) {
            // This can happen if the request body is smaller than the size specified in the
            // Content-Length header, and using tomcat APR connector.
            LOG.error("SocketTimeoutException reading request body from inputstream. error=" + String.valueOf(e.getMessage()));
        }
        catch (IOException e) {
            LOG.error("Exception reading request body from inputstream.", e);
        }
        return body;
    }

}
