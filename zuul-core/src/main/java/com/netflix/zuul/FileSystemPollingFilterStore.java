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
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class FileSystemPollingFilterStore implements FilterStore {
    private final File location;
    private boolean pollingActive = true;
    private final ConcurrentHashMap<String, Filter<?>> compiledFilters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> modDateMap = new ConcurrentHashMap<>();

    public FileSystemPollingFilterStore(File location, long pollIntervalSeconds) {
        this.location = location;
        Thread poller = new Thread(getClass().getName() + "-Poller") {
            public void run() {
                System.out.println("Polling thread starting up!");
                while(pollingActive) {
                    try {
                        sleep(pollIntervalSeconds * 1000);
                        refreshInMemoryFilters();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        };
        poller.start();
    }

    protected abstract FileFilter getFileFilter();

    protected abstract Class<?> getClassFromFilterFile(File f) throws IOException;

    @Override
    public FiltersForRoute getFilters(IngressRequest ingressReq) throws IOException {
        List<PreFilter> preFilters = pickFilters(compiledFilters.values(), PreFilter.class);
        List<RouteFilter> routeFilters = pickFilters(compiledFilters.values(), RouteFilter.class);
        List<PostFilter> postFilters = pickFilters(compiledFilters.values(), PostFilter.class);
        List<ErrorFilter> errorFilters = pickFilters(compiledFilters.values(), ErrorFilter.class);
        if (routeFilters.size() != 1) {
            throw new IllegalStateException("No route filter provided - not a valid configuration!");
        }
        if (errorFilters.size() > 1) {
            throw new IllegalStateException("More than 1 error filter provided - not a valid configuration");
        }
        return new FiltersForRoute(preFilters, routeFilters.get(0), postFilters, (errorFilters.size() == 0) ? null : errorFilters.get(0));
    }

    private static <T extends Filter> List<T> pickFilters(Collection<Filter<?>> filters, Class<T> clazz) {
        List<T> picked = new ArrayList<>();
        for (Filter<?> compiledFilter: filters) {
            if (clazz.isAssignableFrom(compiledFilter.getClass())) {
                picked.add((T) compiledFilter);
            }
        }
        return picked;
    }

    private void refreshInMemoryFilters() throws IOException {
        System.out.println("Woke up and scanning files at : " + location);
        Map<File, Long> onDiskLastModDateMap = createMapOnFiltersOnDisk();
        List<String> deletedFromDisk = getFilesDeletedFromDisk(onDiskLastModDateMap);
        for (String pathToDelete: deletedFromDisk) {
            System.out.println("Removing filter from processor : " + pathToDelete);
            if (compiledFilters.containsKey(pathToDelete)) {
                compiledFilters.remove(pathToDelete);
            }
            if (modDateMap.containsKey(pathToDelete)) {
                modDateMap.remove(pathToDelete);
            }
        }
        for (File fileOnDisk: onDiskLastModDateMap.keySet()) {
            try {
                String pathToUpdate = fileOnDisk.getCanonicalPath();
                if (isNewerThanCached(fileOnDisk)) {
                    Class<?> filterClass = getClassFromFilterFile(fileOnDisk);
                    if (Filter.class.isAssignableFrom(filterClass)) {
                        Filter filter = (Filter) filterClass.newInstance();
                        if (modDateMap.containsKey(pathToUpdate)) {
                            System.out.println("Updated : " + pathToUpdate + " : " + fileOnDisk.lastModified() + " : " + filter);
                        } else {
                            System.out.println("Added : " + pathToUpdate + " : " + fileOnDisk.lastModified() + " : " + filter);
                        }
                        compiledFilters.put(pathToUpdate, filter);
                        modDateMap.put(pathToUpdate, fileOnDisk.lastModified());
                    }
                }
            } catch (Exception ex){
                System.err.println("Error loading Filter from : " + fileOnDisk + " : " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    private List<String> getFilesDeletedFromDisk(Map<File, Long> onDiskFiles) throws IOException {
        List<String> deletedPaths = new ArrayList<>();
        for (String path: modDateMap.keySet()) {
            boolean found = false;
            for (File fileOnDisk: onDiskFiles.keySet()) {
                if (fileOnDisk.getCanonicalPath().equals(path)) {
                    found = true;
                }
            }
            if (!found) {
                deletedPaths.add(path);
            }
        }
        return deletedPaths;
    }

    private Map<File, Long> createMapOnFiltersOnDisk() throws IOException {
        List<File> allFiltersOnDisk = getAllMatchingFilters(location, getFileFilter(), new ArrayList<File>());
        //System.out.println("Found " + allFiltersOnDisk.size() + " files at location : " + location);
        Map<File, Long> onDiskLastModDateMap = new HashMap<>();
        for (File f: allFiltersOnDisk) {
            onDiskLastModDateMap.put(f, f.lastModified());
            //System.out.println("On Disk : " + f.getCanonicalPath() + " -> " + f.lastModified());
        }
        return onDiskLastModDateMap;
    }

    private List<File> getAllMatchingFilters(File location, FileFilter fileFilter, List<File> initialList) throws IOException {
        //System.out.println("Exploring files at : " + location.getCanonicalPath());
        if (location.isDirectory()) {
            List<File> filesSoFar = new ArrayList<>(initialList);
            for (File f: location.listFiles(fileFilter)) {
                List<File> result = getAllMatchingFilters(f, fileFilter, filesSoFar);
                filesSoFar.addAll(result);
            }
            return filesSoFar;
        } else {
            return new ArrayList<>(Arrays.asList(location));
        }
    }

    private boolean isNewerThanCached(File f) throws IOException {
        Long lastKnownModifiedDate = modDateMap.get(f.getCanonicalPath());
        if (lastKnownModifiedDate != null) {
            return f.lastModified() > lastKnownModifiedDate;
        } else {
            return true;
        }
    }
}