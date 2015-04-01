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

import com.netflix.zuul.filter.*;
import com.netflix.zuul.lifecycle.FiltersForRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public abstract class FileSystemPollingFilterStore<State> extends FilterStore<State> {
    private final static Logger logger = LoggerFactory.getLogger(FileSystemPollingFilterStore.class);

    private final List<File> locations;
    private final long pollIntervalSeconds;
    private boolean pollingActive = true;
    private final ConcurrentHashMap<String, Filter> compiledFilters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> modDateMap = new ConcurrentHashMap<>();

    public FileSystemPollingFilterStore(List<File> locations, long pollIntervalSeconds) {
        this.locations = locations;
        this.pollIntervalSeconds = pollIntervalSeconds;
    }

    @Override
    public void init()
    {
        // Run refreshing the filters inline now.
        try {
            refreshInMemoryFilters();
        }
        catch (IOException e) {
            throw new RuntimeException("Error initializing the FilterStore. locations=" + getLocationsAsText(), e);
        }

        // And then setup and start a polling thread.
        Thread poller = new Thread(getClass().getName() + "-Poller") {
            public void run() {
                logger.info("Filesystem-scanning thread starting up and looking for filters at :" + getLocationsAsText());
                while(pollingActive) {
                    try {
                        sleep(pollIntervalSeconds * 1000);
                        refreshInMemoryFilters();
                    } catch (InterruptedException ex) {
                        interrupt();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        };
        poller.start();
    }

    public String getLocationsAsText() {
        if (locations == null)
            return "";
        return locations.stream().map(f -> f.getAbsolutePath()).collect(Collectors.joining(","));
    }

    protected abstract FileFilter getFileFilter();

    protected abstract Class<?> getClassFromFilterFile(File f) throws IOException;

    @Override
    public FiltersForRoute<State> fetchFilters() throws IOException {
        List<PreFilter> preFilters = pickFilters(compiledFilters.values(), PreFilter.class);
        List<RouteFilter> routeFilters = pickFilters(compiledFilters.values(), RouteFilter.class);
        List<PostFilter> postFilters = pickFilters(compiledFilters.values(), PostFilter.class);
        List<ErrorFilter> errorFilters = pickFilters(compiledFilters.values(), ErrorFilter.class);
        if (routeFilters.size() != 1) {
            logger.error("Found " + routeFilters.size() + " route filters");
            for (RouteFilter routeFilter: routeFilters) {
                logger.error(" RouteFilter : " + routeFilter.getClass().getSimpleName());
            }
            throw new IllegalStateException("No route filter provided - not a valid configuration!");
        }
        if (errorFilters.size() > 1) {
            throw new IllegalStateException("More than 1 error filter provided - not a valid configuration");
        }

        @SuppressWarnings({"rawtype", "unchecked"})
        FiltersForRoute<State> filtersForRoute = new FiltersForRoute(preFilters, routeFilters.get(0), postFilters,
                                                              (errorFilters.size() == 0) ? null : errorFilters.get(0));
        return filtersForRoute;
    }

    private static <T extends Filter> List<T> pickFilters(Collection<Filter> filters, Class<T> clazz) {
        List<T> picked = new ArrayList<>();
        for (Filter compiledFilter: filters) {
            if (clazz.isAssignableFrom(compiledFilter.getClass())) {
                @SuppressWarnings("unchecked")
                T typedCompiledFilter = (T) compiledFilter;
                picked.add(typedCompiledFilter);
            }
        }
        return picked;
    }

    private void refreshInMemoryFilters() throws IOException {
        if (logger.isDebugEnabled()) {
            logger.debug("Woke up and scanning files at : " + getLocationsAsText());
        }
        Map<File, Long> onDiskLastModDateMap = createMapOnFiltersOnDisk();
        List<String> deletedFromDisk = getFilesDeletedFromDisk(onDiskLastModDateMap);
        for (String pathToDelete: deletedFromDisk) {
            logger.info("Removing filter from processor : " + pathToDelete);
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
                            logger.info("Updated : " + pathToUpdate + " : " + fileOnDisk.lastModified() + " : " + filter);
                        } else {
                            logger.info("Added : " + pathToUpdate + " : " + fileOnDisk.lastModified() + " : " + filter);
                        }
                        compiledFilters.put(pathToUpdate, filter);
                        modDateMap.put(pathToUpdate, fileOnDisk.lastModified());
                    }
                }
            } catch (Exception ex){
                logger.error("Error loading Filter from : " + fileOnDisk + " : " + ex.getMessage());
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
        List<File> allFiltersOnDisk = getAllMatchingFiltersForLocations(locations, getFileFilter(), new ArrayList<>());

        Map<File, Long> onDiskLastModDateMap = new HashMap<>();
        for (File f: allFiltersOnDisk) {
            onDiskLastModDateMap.put(f, f.lastModified());
            logger.debug("On Disk : " + f.getCanonicalPath() + " -> " + f.lastModified());
        }
        return onDiskLastModDateMap;
    }

    private List<File> getAllMatchingFiltersForLocations(List<File> locations,
                                                         FileFilter fileFilter, List<File> initialList) throws IOException {
        List<File> allFiles = new ArrayList<>();
        for (File location : locations) {
            List<File> filesInThisLocation = getAllMatchingFilters(location, fileFilter, initialList);
            allFiles.addAll(filesInThisLocation);
        }
        return allFiles;
    }

    private List<File> getAllMatchingFilters(File location, FileFilter fileFilter, List<File> initialList) throws IOException {
        logger.debug("Exploring files at : " + location.getCanonicalPath());
        List<File> filesSoFar;
        if (location.isDirectory()) {
            filesSoFar = new ArrayList<>(initialList);
            for (File f: location.listFiles(fileFilter)) {
                List<File> result = getAllMatchingFilters(f, fileFilter, filesSoFar);
                filesSoFar.addAll(result);
            }
            return filesSoFar;
        } else {
            filesSoFar = new ArrayList<>(Arrays.asList(location));
        }

        logger.debug("Found " + filesSoFar.size() + " files at location : " + location);
        return filesSoFar;
    }

    private boolean isNewerThanCached(File f) throws IOException {
        Long lastKnownModifiedDate = modDateMap.get(f.getCanonicalPath());
        return lastKnownModifiedDate == null || f.lastModified() > lastKnownModifiedDate;
    }
}