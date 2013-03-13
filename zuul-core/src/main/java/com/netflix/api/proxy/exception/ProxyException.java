package com.netflix.api.proxy.exception;

import com.netflix.api.proxy.monitoring.CounterFactory;

/**
 * Created by IntelliJ IDEA.
 * User: mcohen
 * Date: 10/20/11
 * Time: 4:33 PM
 * To change this template use File | Settings | File Templates.
 */
public class ProxyException extends Exception {
    public int nStatusCode;
    public String errorCause;

    public ProxyException(Throwable throwable, String sMessage, int nStatusCode, String errorCause) {
        super(sMessage, throwable);
        this.nStatusCode = nStatusCode;
        this.errorCause = errorCause;
        incrementCounter("ZUUL::EXCEPTION:" + errorCause + ":" + nStatusCode);
    }

    public ProxyException(String sMessage, int nStatusCode, String errorCause) {
        super(sMessage);
        this.nStatusCode = nStatusCode;
        this.errorCause = errorCause;
        incrementCounter("ZUUL::EXCEPTION:"+errorCause +":"+nStatusCode);

    }

    public ProxyException(Throwable throwable, int nStatusCode, String errorCause) {
        super(throwable.getMessage(), throwable);
        this.nStatusCode = nStatusCode;
        this.errorCause = errorCause;
        incrementCounter("ZUUL::EXCEPTION:"+errorCause +":"+nStatusCode);

    }

    private static final void incrementCounter(String name) {
        CounterFactory.instance().increment(name);
    }

}
