package com.netflix.zuul;


import com.netflix.zuul.context.SessionContext;

public final class ZuulFilterResult {
        
    private SessionContext result;
    private Throwable exception;
    private ExecutionStatus status;
    
    public ZuulFilterResult(SessionContext result, ExecutionStatus status) {
        this.result = result;
        this.status = status;
    }
    
    public ZuulFilterResult(ExecutionStatus status) {
        this.status = status;
    }

    public ZuulFilterResult() {
        this.status = ExecutionStatus.DISABLED;
    }

    /**
     * @return the result
     */
    public SessionContext getResult() {
        return result;
    }
    /**
     * @param result the result to set
     */
    public void setResult(SessionContext result) {
        this.result = result;
    }

    /**
     * @return the status
     */
    public ExecutionStatus getStatus() {
        return status;
    }

    /**
     * @param status the status to set
     */
    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    /**
     * @return the exception
     */
    public Throwable getException() {
        return exception;
    }

    /**
     * @param exception the exception to set
     */
    public void setException(Throwable exception) {
        this.exception = exception;
    }
    
}
