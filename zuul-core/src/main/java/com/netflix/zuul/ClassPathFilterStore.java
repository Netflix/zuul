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
package com.netflix.zuul;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class ClassPathFilterStore implements FilterStore {
    private final String packagePrefix;
    private final InMemoryFilterStore backingFilterStore;
    private AtomicBoolean filterStoreInitialized = new AtomicBoolean(false);

    public ClassPathFilterStore(String packagePrefix) {
        this.packagePrefix = packagePrefix;
        backingFilterStore = new InMemoryFilterStore();
    }

    @Override
    public FiltersForRoute getFilters(IngressRequest ingressReq) throws IOException {
        System.out.println("Getting filters from the classpath with prefix : " + packagePrefix);
        if (filterStoreInitialized.get()) {
            return backingFilterStore.getFilters(ingressReq);
        } else {
            System.out.println("Classpath has not been scanned yet, doing that now");
            boolean noErrorsOccurred = true;
            try {
                List<File> files = new ArrayList<>();
                Enumeration<URL> classPathRoots = ClassLoader.getSystemClassLoader().getResources("");
                while (classPathRoots.hasMoreElements()) {
                    URL classPathRoot = classPathRoots.nextElement();
                    File rootFile = new File(classPathRoot.getPath());
                    Predicate<String> fileMatcher = getMatcher(rootFile, packagePrefix);
                    addAllNestedFiles(files, rootFile, fileMatcher);
                    for (File f : files) {
                        try {
                            String className = getClassNameFromFileName(f, rootFile);
                            Class<?> clazz = Class.forName(className);
                            if (Filter.class.isAssignableFrom(clazz)) {
                                Filter filter = (Filter) clazz.newInstance();
                                System.out.println("Got filter : " + filter);
                                backingFilterStore.addFilter(filter);
                            }
                        } catch (ClassNotFoundException cnfe) {
                            System.out.println("Couldn't get class from : " + f);
                            noErrorsOccurred = false;
                        } catch (InstantiationException ie) {
                            System.out.println("Couldn't instantiate class from : " + f);
                            noErrorsOccurred = false;
                        } catch (IllegalAccessException iae) {
                            System.out.println("Illegal access when creating class from : " + f);
                            noErrorsOccurred = false;
                        }
                    }
                }
                if (noErrorsOccurred) {
                    filterStoreInitialized.compareAndSet(false, true);
                }
                return backingFilterStore.getFilters(ingressReq);
            } catch (IOException ioe) {
                System.err.println("Error getting the classpath - no filters found!");
                throw ioe;
            }
        }
    }

    private void addAllNestedFiles(List<File> fileList, File root, Predicate<String> matcher) {
        if (root.isDirectory()) {
            for (File leaf: root.listFiles()) {
                addAllNestedFiles(fileList, leaf, matcher);
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