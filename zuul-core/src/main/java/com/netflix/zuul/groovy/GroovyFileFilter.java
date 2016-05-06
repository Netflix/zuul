package com.netflix.zuul.groovy;

/**
 * Created with IntelliJ IDEA.
 * User: mcohen
 * Date: 5/30/13
 * Time: 11:47 AM
 * To change this template use File | Settings | File Templates.
 */

import java.io.File;
import java.io.FilenameFilter;

/**
 * Filters only .groovy files
 */
public class GroovyFileFilter implements FilenameFilter {
    public boolean accept(File dir, String name) {
        return name.endsWith(".groovy");
    }
}