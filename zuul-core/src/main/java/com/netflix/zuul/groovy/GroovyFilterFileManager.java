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
package com.netflix.zuul.groovy;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

/**
 * @author Mikey Cohen
 * Date: 12/7/11
 * Time: 12:09 PM
 */
public class GroovyFilterFileManager {

    String[] aDirectories;
    int pollingIntervalSeconds;
    Thread poller;
    boolean bRunning = true;

    static final GroovyFileFilter GROOVY_FILE_FILTER = new GroovyFileFilter();

    static GroovyFilterFileManager INSTANCE;

    private GroovyFilterFileManager() {
    }


    public static void init(int pollingIntervalSeconds, String... directories) throws IOException, IllegalAccessException, InstantiationException {
        INSTANCE = new GroovyFilterFileManager();
        INSTANCE.aDirectories = directories;
        INSTANCE.pollingIntervalSeconds = pollingIntervalSeconds;
        INSTANCE.manageFiles();
        INSTANCE.startPoller();

    }

    public static void shutdown(){
        INSTANCE.stopPoller();
    }

    void stopPoller() {
        bRunning = false;
    }

    void startPoller() {
        poller = new Thread("FilterManagerPoller") {
            public void run() {
                while (bRunning) {
                    try {
                        sleep(pollingIntervalSeconds * 1000);
                        manageFiles();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        poller.start();
    }


    public File getDirectory(String sPath) {
        File directory = new File(sPath);
        if (!directory.isDirectory()) throw new RuntimeException(sPath + " is not a valid directory");
        return directory;

    }

    List<File> getFiles() {
        List<File> list = new ArrayList<File>();
        for (String sDirectory : aDirectories) {
            File directory = getDirectory(sDirectory);
            File[] aFiles = directory.listFiles(GROOVY_FILE_FILTER);
            if (aFiles != null) {
                list.addAll(Arrays.asList(aFiles));
            }
        }
        return list;
    }

    void processGroovyFiles(List<File> aFiles) throws IOException, InstantiationException, IllegalAccessException {

        for (File file : aFiles) {
            FilterLoader.getInstance().putFilter(file);
        }
    }

    void manageFiles() throws IOException, IllegalAccessException, InstantiationException {
        List<File> aFiles = getFiles();
        processGroovyFiles(aFiles);
    }


    static class GroovyFileFilter implements FilenameFilter {
        public boolean accept(File dir, String name) {
            return name.endsWith(".groovy");
        }
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class UnitTest {

        @Mock
        private File nonGroovyFile;
        @Mock
        private File groovyFile;

        @Mock
        private File directory;

        @Before
        public void before() {
            MockitoAnnotations.initMocks(this);
        }

        @Test
        public void testGroovyFileFilter() {

            when(nonGroovyFile.getName()).thenReturn("file.mikey");
            when(groovyFile.getName()).thenReturn("file.groovy");

            GroovyFileFilter filter = new GroovyFileFilter();

            assertFalse(filter.accept(nonGroovyFile, "file.mikey"));
            assertTrue(filter.accept(groovyFile, "file.groovy"));

        }

        @Test
        public void testGroovyFileLoad() {

            when(nonGroovyFile.getName()).thenReturn("file.mikey");
            when(groovyFile.getName()).thenReturn("file.groovy");

            File[] aFiles = new File[2];
            aFiles[0] = nonGroovyFile;
            aFiles[1] = groovyFile;

            when(directory.listFiles(GROOVY_FILE_FILTER)).thenReturn(aFiles);
            when(directory.isDirectory()).thenReturn(true);

            GroovyFilterFileManager manager = new GroovyFilterFileManager();
            manager = spy(manager);

            doReturn(directory).when(manager).getDirectory("test");
            manager.aDirectories = new String[1];
            manager.aDirectories[0] = "test";
            List files = manager.getFiles();
            assertTrue(files.size() == 2);


        }

        @Test
        public void testFileManagerInit() throws IOException, InstantiationException, IllegalAccessException {
            GroovyFilterFileManager manager = new GroovyFilterFileManager();
            manager = spy(manager);
            doNothing().when(manager).manageFiles();
            manager.init(1, "test", "test1");
            verify(manager, atLeast(1)).manageFiles();
            verify(manager, times(1)).startPoller();
            assertNotNull(manager.poller);

        }

    }

}
