package com.netflix.zuul.http;

import javax.servlet.http.HttpServletResponse;

/**
 * ServletResponseWrapper which delegates all to underlying ServletResponse, except for:
 * <ul>
 *     <li>Records the status code set on the response.</li>
 * </ul>
 *
 * User: michaels@netflix.com
 * Date: 2/19/14
 * Time: 5:56 PM
 */
public class HttpServletResponseWrapper extends javax.servlet.http.HttpServletResponseWrapper {
    private int status = 0;

    public HttpServletResponseWrapper(HttpServletResponse response) {
        super(response);
    }

    @Override
    public void setStatus(int sc) {
        this.status = sc;
        super.setStatus(sc);
    }

    @Override
    public void setStatus(int sc, String sm) {
        this.status = sc;
        super.setStatus(sc, sm);
    }

    public int getStatus() {
        return status;
    }
}
