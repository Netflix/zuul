package com.netflix.zuul;

/**
 * User: Mike Smith
 * Date: 3/3/15
 * Time: 12:14 PM
 */
public class StartupState
{
    private static final StartupState INSTANCE = new StartupState();
    private boolean started;

    public static StartupState getInstance() {
        return INSTANCE;
    }

    public boolean isStarted() {
        return started;
    }

    public void setStarted(boolean started) {
        this.started = started;
    }
}
