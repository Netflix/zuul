package com.netflix.zuul;

import com.netflix.zuul.FilterFileManager.FilterFileManagerConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.File;

@RunWith(MockitoJUnitRunner.class)
public class FilterFileManagerTest {
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

        manager = Mockito.spy(manager);
        Mockito.doNothing().when(manager).manageFiles();

        manager.init();
        Mockito.verify(manager, Mockito.atLeast(1)).manageFiles();
        Mockito.verify(manager, Mockito.times(1)).startPoller();
        Assert.assertNotNull(manager.poller);
    }
}