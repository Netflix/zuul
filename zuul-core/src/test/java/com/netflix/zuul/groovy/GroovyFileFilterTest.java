package com.netflix.zuul.groovy;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class GroovyFileFilterTest {
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
}