package com.netflix.zuul.context;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * User: Mike Smith
 * Date: 5/5/15
 * Time: 4:07 PM
 */
public class ServletResponseMessageWriter
{
    private static final Logger LOG = LoggerFactory.getLogger(ServletResponseMessageWriter.class);

    public void write(SessionContext ctx, HttpServletResponse servletResponse)
    {
        HttpResponseMessage responseMessage = (HttpResponseMessage) ctx.getResponse();
        if (responseMessage == null) {
            throw new RuntimeException("Null HttpResponseMessage when attempting to write to ServletResponse!");
        }

        // Status.
        servletResponse.setStatus(responseMessage.getStatus());

        // Headers.
        for (Map.Entry<String, String> header : responseMessage.getHeaders().entries()) {
            servletResponse.setHeader(header.getKey(), header.getValue());
        }

        // Body.
        if (responseMessage.getBody() != null) {
            try {
                ServletOutputStream output = servletResponse.getOutputStream();
                IOUtils.write(responseMessage.getBody(), output);
                output.flush();
            }
            catch (IOException e) {
                throw new RuntimeException("Error writing response body to outputstream!", e);
            }
        }
    }
}
