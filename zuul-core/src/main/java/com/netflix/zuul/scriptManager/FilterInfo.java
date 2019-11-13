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
package com.netflix.zuul.scriptManager;

import com.netflix.zuul.filters.FilterType;
import net.jcip.annotations.ThreadSafe;

import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Representation of a ZuulFilter for representing and storing in a database
 */
@ThreadSafe
public class FilterInfo implements  Comparable<FilterInfo>{

    private final String filter_id;
    private final String filter_name;
    private final String filter_code;
    private final FilterType filter_type;
    private final String filter_disablePropertyName;
    private final String filter_order;
    private final String application_name;
    private int revision;
    private Date creationDate;
    /* using AtomicBoolean so we can pass it into EndpointScriptMonitor */
    private final AtomicBoolean isActive = new AtomicBoolean();
    private final AtomicBoolean isCanary = new AtomicBoolean();

    /**
     * Constructor
     * @param filter_id
     * @param filter_code
     * @param filter_type
     * @param filter_name
     * @param disablePropertyName
     * @param filter_order
     * @param application_name
     */
    public FilterInfo(String filter_id, String filter_code, FilterType filter_type, String filter_name, String disablePropertyName, String filter_order, String application_name) {
        this.filter_id = filter_id;
        this.filter_code = filter_code;
        this.filter_type = filter_type;
        this.filter_name = filter_name;
        this.filter_disablePropertyName = disablePropertyName;
        this.filter_order = filter_order;
        this.application_name = application_name;
        isActive.set(false);
        isCanary.set(false);
    }

    /**
     *
     * @return the filter name; the class name of the filter
     */
    public String getFilterName() {
        return filter_name;
    }

    /**
     * the Source code for the filter
     * @return the Source code for the filter
     */
    public String getFilterCode() {
        return filter_code;
    }


    /**
     *
     * @return the name of the property to disable the filter.
     */
    public String getFilterDisablePropertyName() {
        return filter_disablePropertyName;
    }

    /**
     *
     * @return the filter_type
     */
    public FilterType getFilterType() {
        return filter_type;
    }


    @Override
    public String toString() {
        return "FilterInfo{" +
                "filter_id='" + filter_id + '\'' +
                ", filter_name='" + filter_name + '\'' +
                ", filter_type='" + filter_type + '\'' +
                ", revision=" + revision +
                ", creationDate=" + creationDate +
                ", isActive=" + isActive +
                ", isCanary=" + isCanary +
                ", application_name=" + application_name +
                '}';
    }

    /**
     * the application name context of the filter. This is for if Zuul is applied to different applications in the same datastor
     * @return
     */
    public String getApplication_name() {
        return application_name;
    }

    /**
     *
     * @param filter_id
     * @param revision
     * @param creationDate
     * @param isActive
     * @param isCanary
     * @param filter_code
     * @param filter_type
     * @param filter_name
     * @param disablePropertyName
     * @param filter_order
     * @param application_name
     */
    public FilterInfo(String filter_id, int revision, Date creationDate, boolean isActive, boolean isCanary, String filter_code, FilterType filter_type, String filter_name, String disablePropertyName, String filter_order, String application_name) {
        this.filter_id = filter_id;
        this.revision = revision;
        this.creationDate = creationDate;
        this.isActive.set(isActive);
        this.isCanary.set(isCanary);
        this.filter_code = filter_code;
        this.filter_name = filter_name;
        this.filter_type = filter_type;
        this.filter_order = filter_order;
        this.filter_disablePropertyName = disablePropertyName;
        this.application_name = application_name;

    }

    /**
     *
     * @return the revision of this filter
     */
    public int getRevision() {
        return revision;
    }

    /**
     *
     * @return creation date
     */
    public Date getCreationDate() {
        return creationDate;
    }

    /**
     *
     * @return true if this filter is active
     */
    public boolean isActive() {
        return isActive.get();
    }

    /**
     *
     * @return true if this filter should be active a "canary" cluster. A "canary" cluster is a separate cluster
     * where filters may be tested before going to the full production cluster.
     */
    public boolean isCanary() {
        return isCanary.get();
    }


    /**
     *
     * @return unique key for the filter
     */
    public String getFilterID() {
        return filter_id;
    }

    /**
     *
     * @return the filter order
     */
    public String getFilterOrder() {
        return filter_order;
    }

    /**
     * builds the unique filter_id key
     * @param application_name
     * @param filter_type
     * @param filter_name
     * @return key is application_name:filter_name:filter_type
     */
    public static String buildFilterID(String application_name, FilterType filter_type, String filter_name) {
        return application_name + ":" + filter_name + ":" + filter_type.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FilterInfo that = (FilterInfo) o;

        if (revision != that.revision) return false;
        if (creationDate != null ? !creationDate.equals(that.creationDate) : that.creationDate != null) return false;
        if (filter_code != null ? !filter_code.equals(that.filter_code) : that.filter_code != null) return false;
        if (filter_id != null ? !filter_id.equals(that.filter_id) : that.filter_id != null) return false;
        if (filter_name != null ? !filter_name.equals(that.filter_name) : that.filter_name != null) return false;
        if (filter_type != null ? !filter_type.equals(that.filter_type) : that.filter_type != null) return false;
        if (isActive != null ? !(isActive.get() == that.isActive.get()) : that.isActive != null) return false;
        if (isCanary != null ? !(isCanary.get() == that.isCanary.get()) : that.isCanary != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = filter_id != null ? filter_id.hashCode() : 0;
        result = 31 * result + (filter_name != null ? filter_name.hashCode() : 0);
        result = 31 * result + (filter_code != null ? filter_code.hashCode() : 0);
        result = 31 * result + (filter_type != null ? filter_type.hashCode() : 0);
        result = 31 * result + revision;
        result = 31 * result + (creationDate != null ? creationDate.hashCode() : 0);
        result = 31 * result + (isActive != null ? isActive.hashCode() : 0);
        result = 31 * result + (isCanary != null ? isCanary.hashCode() : 0);
        return result;
    }

    @Override
    public int compareTo(FilterInfo filterInfo) {
        if(filterInfo.getFilterName().equals(this.getFilterName())){
            return filterInfo.creationDate.compareTo(getCreationDate());
        }
        return filterInfo.getFilterName().compareTo(this.getFilterName());
    }

}
