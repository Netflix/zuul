/*
 * Copyright 2018 Netflix, Inc.
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

import com.netflix.config.DynamicIntProperty;
import com.netflix.zuul.groovy.GroovyFileFilter;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

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
    private static final DynamicIntProperty FILE_PROCESSOR_THREADS = new DynamicIntProperty("zuul.filterloader.threads", 1);
    private static final DynamicIntProperty FILE_PROCESSOR_TASKS_TIMEOUT_SECS = new DynamicIntProperty("zuul.filterloader.tasks.timeout", 120);

    Thread poller;
    boolean bRunning = true;

    private final FilterFileManagerConfig config;
    private final FilterLoader filterLoader;
    private final ExecutorService processFilesService;

    @Inject
    public FilterFileManager(FilterFileManagerConfig config, FilterLoader filterLoader) {
        this.config = config;
        this.filterLoader = filterLoader;

        BasicThreadFactory threadFactory = new BasicThreadFactory.Builder()
                .namingPattern("FilterFileManager_ProcessFiles-%d")
                .build();
        this.processFilesService = Executors.newFixedThreadPool(FILE_PROCESSOR_THREADS.get(), threadFactory);
    }


    /**
     * Initialized the GroovyFileManager.
     *
     * @throws Exception
     */
    @PostConstruct
    public void init() throws Exception
    {
        long startTime = System.currentTimeMillis();
        
        filterLoader.putFiltersForClasses(config.getClassNames());
        manageFiles();
        startPoller();
        
        LOG.warn("Finished loading all zuul filters. Duration = " + (System.currentTimeMillis() - startTime) + " ms.");
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
            try {
                directory = new File(resource.toURI());
            } catch (Exception e) {
                LOG.error("Error accessing directory in classloader. path=" + sPath, e);
            }
            if (!directory.isDirectory()) {
                throw new RuntimeException(directory.getAbsolutePath() + " is not a valid directory");
            }
        }
        return directory;
    }

    /**
     * Returns a List<File> of all Files from all polled directories
     *
     * @return
     */
    List<File> getFiles() {
        List<File> list = new ArrayList<File>();
        for (String sDirectory : config.getDirectories()) {
            if (sDirectory != null) {
                File directory = getDirectory(sDirectory);
                File[] aFiles = directory.listFiles(config.getFilenameFilter());
                if (aFiles != null) {
                    list.addAll(Arrays.asList(aFiles));
                }
            }
        }
        return list;
    }

    /**
     * puts files into the FilterLoader. The FilterLoader will only add new or changed filters
     *
     * @param aFiles a List<File>
     * @throws IOException
     * @throws InstantiationException
     * @throws IllegalAccessException
     */
    void processGroovyFiles(List<File> aFiles) throws Exception {

        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (File file : aFiles) {
            tasks.add(() -> {
                try {
                    return filterLoader.putFilter(file);
                }
                catch(Exception e) {
                    LOG.error("Error loading groovy filter from disk! file = " + String.valueOf(file), e);
                    return false;
                }
            });
        }
        processFilesService.invokeAll(tasks, FILE_PROCESSOR_TASKS_TIMEOUT_SECS.get(), TimeUnit.SECONDS);
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


    @RunWith(MockitoJUnitRunner.class)
    public static class UnitTest
    {
        @Mock
        private File nonGroovyFile;
        @Mock
        private File groovyFile;
        @Mock
        private File directory;
        @Mock
        private FilterLoader filterLoader;

        @Before
        public void before() {
            MockitoAnnotations.initMocks(this);
        }

        @Test
        public void testFileManagerInit() throws Exception
        {
            FilterFileManagerConfig config = new FilterFileManagerConfig(new String[]{"test", "test1"}, new String[]{"com.netflix.blah.SomeFilter"}, 1);
            FilterFileManager manager = new FilterFileManager(config, filterLoader);

            manager = spy(manager);
            doNothing().when(manager).manageFiles();

            manager.init();
            verify(manager, atLeast(1)).manageFiles();
            verify(manager, times(1)).startPoller();
            assertNotNull(manager.poller);
        }
    }
}
