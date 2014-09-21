/**
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul.filterstore;

import com.netflix.zuul.filter.Filter;
import com.netflix.zuul.lifecycle.FiltersForRoute;
import com.netflix.zuul.lifecycle.IngressRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class ClassPathFilterStore<State> extends FilterStore<State> {
    private final Logger logger = LoggerFactory.getLogger(ClassPathFilterStore.class);

    private final String[] packagePrefixes;
    private final InMemoryFilterStore<State> backingFilterStore;
    private AtomicBoolean filterStoreInitialized = new AtomicBoolean(false);

    public ClassPathFilterStore(String... packagePrefixes) {
        this.packagePrefixes = packagePrefixes;
        backingFilterStore = new InMemoryFilterStore<>();
    }

    @Override
    public FiltersForRoute<State> fetchFilters(IngressRequest ingressReq) throws IOException {
        logger.info("Getting filters from the classpath with prefix : " + Arrays.toString(packagePrefixes));
        if (filterStoreInitialized.get()) {
            return backingFilterStore.getFilters(ingressReq);
        } else {
            logger.info("Classpath has not been scanned yet, doing that now");
            boolean noErrorsOccurred = true;
            try {
                List<File> files = new ArrayList<>();
                Enumeration<URL> classPathRoots = ClassLoader.getSystemClassLoader().getResources("");
                while (classPathRoots.hasMoreElements()) {
                    URL classPathRoot = classPathRoots.nextElement();
                    File rootFile = new File(classPathRoot.getPath());
                    for (String packagePrefix: packagePrefixes) {
                        Predicate<String> fileMatcher = getMatcher(rootFile, packagePrefix);
                        addAllNestedFiles(files, rootFile, fileMatcher);
                    }
                    for (File f : files) {
                        try {
                            String className = getClassNameFromFileName(f, rootFile);
                            Class<?> clazz = Class.forName(className);
                            if (Filter.class.isAssignableFrom(clazz)) {
                                Filter filter = (Filter) clazz.newInstance();
                                logger.info("Found filter : " + filter);
                                backingFilterStore.addFilter(filter);
                            }
                        } catch (ClassNotFoundException cnfe) {
                            logger.error("Couldn't get class from : " + f);
                            noErrorsOccurred = false;
                        } catch (InstantiationException ie) {
                            logger.error("Couldn't instantiate class from : " + f);
                            noErrorsOccurred = false;
                        } catch (IllegalAccessException iae) {
                            logger.error("Illegal access when creating class from : " + f);
                            noErrorsOccurred = false;
                        }
                    }
                }
                if (noErrorsOccurred) {
                    filterStoreInitialized.compareAndSet(false, true);
                }
                return backingFilterStore.getFilters(ingressReq);
            } catch (IOException ioe) {
                logger.error("Error getting the classpath - no filters found!");
                throw ioe;
            }
        }
    }

    private void addAllNestedFiles(List<File> fileList, File root, Predicate<String> matcher) {
        if (root.isDirectory()) {
            File[] files = root.listFiles();
            if (null != files) { // listFiles() returns null if the file isn't a directory or an I/O exception occurs.
                for (File leaf: files) {
                    addAllNestedFiles(fileList, leaf, matcher);
                }
            }
        } else {
            if (matcher.test(root.getAbsolutePath())) {
                fileList.add(root);
            }
        }
    }

    /* package-private */ static String getClassNameFromFileName(File f, File root) {
        return f.getAbsolutePath().replaceAll(root.getAbsolutePath(), "").replaceAll(".class", "").substring(1).replaceAll("/", ".");
    }

    /* package-private */ static Predicate<String> getMatcher(File rootFile, String s) {
        if (s == null) return (str -> false);
        String regexString = "^" + rootFile + "/" + s.replaceAll("\\.", "/") + ".*";
        return Pattern.compile(regexString).asPredicate();
    }
}