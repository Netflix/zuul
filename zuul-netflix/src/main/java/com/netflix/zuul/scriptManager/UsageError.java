package com.netflix.zuul.scriptManager;

import java.io.Writer;

import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class UsageError {

    private static final Logger LOGGER = LoggerFactory.getLogger(UsageError.class);

    private final int statusCode;
    private final String message;

    UsageError(int statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }

    void setOn(HttpServletResponse response) {
        response.setStatus(statusCode);
        try {
            Writer w = response.getWriter();
            if (message != null) {
                w.write(message + "\n\n");
            }
            w.write(new UsageDoc().get());
        } catch (Exception e) {
            LOGGER.error("Failed to output usage error.", e);
            // won't throw exception because this is not critical, logging the error is enough
        }
    }
}
