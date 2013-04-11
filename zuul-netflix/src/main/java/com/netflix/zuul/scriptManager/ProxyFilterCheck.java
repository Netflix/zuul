package com.netflix.zuul.scriptManager;

import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicPropertyFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.netflix.config.ChainedDynamicProperty.*;

/**
 * Created with IntelliJ IDEA.
 * User: mcohen
 * Date: 6/15/12
 * Time: 3:44 PM
 * To change this template use File | Settings | File Templates.
 */
public class ProxyFilterCheck {

    Map<String, FilterInfo> runningFilters = new HashMap<String, FilterInfo>();
    ZuulFilterDAO dao;

    DynamicBooleanProperty active = DynamicPropertyFactory.getInstance().getBooleanProperty("zuul.use.active.filters", true);
    DynamicBooleanProperty canary = DynamicPropertyFactory.getInstance().getBooleanProperty("zuul.use.canary.filters", false);


    private static  ProxyFilterCheck INSTANCE;


    /**
     * Starts the check against the ZuulFilter data store for changed or new filters.
     * @param dao
     */
    public static void start(ZuulFilterDAO dao) {

        INSTANCE = new ProxyFilterCheck(dao);

    }



    public ProxyFilterCheck(ZuulFilterDAO dao) {
        this.dao = dao;
        checkerThread.start();

    }

    public static ProxyFilterCheck getInstance() {
        return INSTANCE;
    }


    boolean running = true;
    private long INTERVAL = 30000; //30 seconds
    Thread checkerThread = new Thread("ProxyFilterChecker") {
        public void run() {
            while (running) {
                try {

                    if (canary.get()) {
                        HashMap<String, FilterInfo> setFilters = new HashMap<String, FilterInfo>();

                        List<FilterInfo> activeScripts = dao.getAllActiveScripts();
                        if (activeScripts != null) {
                            for (FilterInfo newFilter : activeScripts) {
                                setFilters.put(newFilter.getFilterID(), newFilter);
                            }
                        }

                        List<FilterInfo> canaryScripts = dao.getAllCanaryScripts();
                        if (canaryScripts != null) {
                            for (FilterInfo newFilter : canaryScripts) {
                                setFilters.put(newFilter.getFilterID(), newFilter);
                            }
                        }
                        for (FilterInfo next : setFilters.values()) {
                            doFilterCheck(next);
                        }
                    }
                    else if (active.get()) {
                        List<FilterInfo> newFilters = dao.getAllActiveScripts();
                        if (newFilters == null) continue;
                        for (FilterInfo newFilter : newFilters) {
                            doFilterCheck(newFilter);
                        }

                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }

                try {
                    sleep(INTERVAL);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    running = false;
                }
            }
        }
    };

    private void doFilterCheck(FilterInfo newFilter) throws IOException {
        FilterInfo existingFilter = runningFilters.get(newFilter.getFilterID());
        if (existingFilter == null || !existingFilter.equals(newFilter)) {
            System.out.println("adding filter to disk" + newFilter.toString());
            writeFilterToDisk(newFilter);
            runningFilters.put(newFilter.getFilterID(), newFilter);
        }
    }

    private void writeFilterToDisk(FilterInfo newFilter) throws IOException {

        String path = DynamicPropertyFactory.getInstance().getStringProperty("zuul.script.preprocess.path", null).get();
        if (newFilter.getFilterType().equals("post")) {
            path = DynamicPropertyFactory.getInstance().getStringProperty("zuul.script.postprocess.path", null).get();
        }
        if (newFilter.getFilterType().equals("proxy")) {
            path = DynamicPropertyFactory.getInstance().getStringProperty("zuul.script.proxy.path", null).get();
        }

        File f = new File(path, newFilter.getFilterName() + ".groovy");
        FileWriter file = new FileWriter(f);
        BufferedWriter out = new BufferedWriter(file);
        out.write(newFilter.getFilterCode());
        out.close();
        file.close();
        System.out.println("filter written " + f.getPath());
    }




}
