package com.netflix.zuul.context;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Enumeration;
import java.util.Map;

/**
 * User: michaels@netflix.com
 * Date: 4/29/15
 * Time: 11:25 AM
 */
public class ServletSessionContextFactory implements SessionContextFactory<HttpServletRequest, HttpServletResponse>
{
    private static final Logger LOG = LoggerFactory.getLogger(ServletSessionContextFactory.class);

    @Override
    public Observable<ZuulMessage> create(SessionContext context, HttpServletRequest servletRequest)
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
        HttpRequestMessage request = new HttpRequestMessage(context, servletRequest.getProtocol(), servletRequest.getMethod(),
                servletRequest.getRequestURI(), queryParams, reqHeaders, servletRequest.getRemoteAddr(), servletRequest.getScheme());

        // Buffer the request body into a byte array.
        request.setBody(bufferBody(servletRequest));

        // Wrap in an Observable.
        return Observable.just(request);
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

    @Override
    public void write(ZuulMessage msg, HttpServletResponse servletResponse)
    {
        HttpResponseMessage response = (HttpResponseMessage) msg;

        // Status.
        servletResponse.setStatus(response.getStatus());

        // Headers.
        for (Map.Entry<String, String> header : response.getHeaders().entries()) {
            servletResponse.setHeader(header.getKey(), header.getValue());
        }

        // Body.
        if (response.getBody() != null) {
            try {
                ServletOutputStream output = servletResponse.getOutputStream();
                IOUtils.write(response.getBody(), output);
                output.flush();
            }
            catch (IOException e) {
                throw new RuntimeException("Error writing response body to outputstream!", e);
            }
        }
    }
}
