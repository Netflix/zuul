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
 * Created by IntelliJ IDEA.
 * User: mcohen
 * Date: 12/7/11
 * Time: 12:09 PM
 * To change this template use File | Settings | File Templates.
 */
public class GroovyFilterFileManager {

    String[] aDirectories;
    int pollingIntervalSeconds;
    Thread poller;
    boolean bRunning = true;

    static final GroovyFileFilter GROOVY_FILE_FILTER = new GroovyFileFilter();

    public GroovyFilterFileManager() {

    }

    public GroovyFilterFileManager(int pollingIntervalSeconds, String... directories) throws IOException, InstantiationException, IllegalAccessException {
        init(pollingIntervalSeconds, directories);
    }

    void init(int pollingIntervalSeconds, String... directories) throws IOException, IllegalAccessException, InstantiationException {
        this.aDirectories = directories;
        this.pollingIntervalSeconds = pollingIntervalSeconds;
        manageFiles();
        startPoller();
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
            GroovyLoader.getInstance().putFilter(file);
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
