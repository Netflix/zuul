/*
 * Copyright 2013 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */
package com.netflix.zuul;

import com.netflix.zuul.groovy.GroovyFileFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This class manages the directory polling for changes and new Groovy filters.
 * Polling interval and directories are specified in the initialization of the class, and a poller will check
 * for changes and additions.
 *
 * @author Mikey Cohen
 *         Date: 12/7/11
 *         Time: 12:09 PM
 */
@Singleton
public class FilterFileManager {

    private static final Logger LOG = LoggerFactory.getLogger(FilterFileManager.class);

    Thread poller;
    boolean bRunning = true;

    private FilterFileManagerConfig config;
    private FilterLoader filterLoader;

    @Inject
    public FilterFileManager(FilterFileManagerConfig config, FilterLoader filterLoader) {
        this.config = config;
        this.filterLoader = filterLoader;
    }

    /**
     * Initialized the GroovyFileManager.
     *
     * @throws Exception
     */
    @PostConstruct
    public void init() throws Exception
    {
        filterLoader.putFiltersForClasses(config.getClassNames());
        manageFiles();
        startPoller();
    }

    /**
     * Shuts down the poller
     */
    @PreDestroy
    public void shutdown() {
        stopPoller();
    }


    void stopPoller() {
        bRunning = false;
    }

    void startPoller() {
        poller = new Thread("GroovyFilterFileManagerPoller") {
            public void run() {
                while (bRunning) {
                    try {
                        sleep(config.getPollingIntervalSeconds() * 1000);
                        manageFiles();
                    }
                    catch (Exception e) {
                        LOG.error("Error checking and/or loading filter files from Poller thread.", e);
                    }
                }
            }
        };
        poller.start();
    }

    /**
     * Returns the directory File for a path. A Runtime Exception is thrown if the directory is in valid
     *
     * @param sPath
     * @return a File representing the directory path
     */
    public File getDirectory(String sPath) {
        File  directory = new File(sPath);
        if (!directory.isDirectory()) {
            URL resource = FilterFileManager.class.getClassLoader().getResource(sPath);
            if (null != resource) {
                try {
                    directory = new File(resource.toURI());
                } catch (Exception e) {
                    LOG.error("Error accessing directory in classloader. path=" + sPath, e);
                }
            }
        }
        if (!directory.isDirectory()) {
            throw new RuntimeException(directory.getAbsolutePath() + " is not a valid directory.");
        }
        return directory;
    }

    /**
     * Returns a List<File> of all Files from all polled directories
     *
     * @return
     */
    private List<File> getFiles() {
        List<File> toReturn = new ArrayList<>();
        for (String sDirectory : config.getDirectories()) {
            if (sDirectory != null) {
                File directory = getDirectory(sDirectory);
                final List<File> filesForDir = getAllFilesForADir(directory);
                toReturn.addAll(filesForDir);
            }
        }
        return toReturn;
    }

    private List<File> getAllFilesForADir(File directory) {
        final List<File> filesForDir = new ArrayList<>();
        File[] aFiles = directory.listFiles();
        if (null == aFiles) {
            return filesForDir;
        }

        for (File aFile : aFiles) {
            if (aFile.isDirectory()) {
                List<File> allFiles = getAllFilesForADir(aFile);
                filesForDir.addAll(allFiles);
            } else if(config.getFilenameFilter().accept(directory, aFile.getName())) {
                filesForDir.add(aFile);
            }
        }
        return filesForDir;
    }

    /**
     * puts files into the FilterLoader. The FilterLoader will only addd new or changed filters
     *
     * @param aFiles a List<File>
     * @throws IOException
     * @throws InstantiationException
     * @throws IllegalAccessException
     */
    void processGroovyFiles(List<File> aFiles) throws Exception {

        for (File file : aFiles) {
            filterLoader.putFilter(file);
        }
    }

    void manageFiles()
    {
        try {
            List<File> aFiles = getFiles();
            processGroovyFiles(aFiles);
        }
        catch (Exception e) {
            String msg = "Error updating groovy filters from disk!";
            LOG.error(msg, e);
            throw new RuntimeException(msg, e);
        }
    }


    public static class FilterFileManagerConfig
    {
        private String[] directories;
        private String[] classNames;
        private int pollingIntervalSeconds;
        private FilenameFilter filenameFilter;

        public FilterFileManagerConfig(String[] directories, String[] classNames, int pollingIntervalSeconds, FilenameFilter filenameFilter) {
            this.directories = directories;
            this.classNames = classNames;
            this.pollingIntervalSeconds = pollingIntervalSeconds;
            this.filenameFilter = filenameFilter;
        }

        public FilterFileManagerConfig(String[] directories, String[] classNames, int pollingIntervalSeconds) {
            this(directories, classNames, pollingIntervalSeconds, new GroovyFileFilter());
        }

        public String[] getDirectories() {
            return directories;
        }
        public String[] getClassNames()
        {
            return classNames;
        }
        public int getPollingIntervalSeconds() {
            return pollingIntervalSeconds;
        }
        public FilenameFilter getFilenameFilter() {
            return filenameFilter;
        }
    }
}
