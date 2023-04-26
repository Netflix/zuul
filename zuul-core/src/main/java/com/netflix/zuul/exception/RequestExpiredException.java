package com.netflix.zuul.exception;

/**
 * @author Argha C
 * @since 4/26/23
 */
public class RequestExpiredException extends ZuulException {

    public RequestExpiredException(String message) {
        super(message, true);
        setStatusCode(504);
    }
}
