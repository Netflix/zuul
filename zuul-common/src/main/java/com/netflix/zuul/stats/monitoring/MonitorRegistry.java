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
package com.netflix.zuul.stats.monitoring;

/**
 * Registry to register a Counter. a Monitor publisher should  be set to get counter information.
 * If it isn't set, registration will be ignored.
 * @author Mikey Cohen
 * Date: 3/18/13
 * Time: 4:24 PM
 */
public class MonitorRegistry {

    private static  final MonitorRegistry instance = new MonitorRegistry();
    private Monitor publisher;

    /**
     * A Monitor implementation should be set here
     * @param publisher
     */
    public void setPublisher(Monitor publisher) {
        this.publisher = publisher;
    }



    public static MonitorRegistry getInstance() {
        return instance;
    }

    public void registerObject(NamedCount monitorObj) {
      if(publisher != null) publisher.register(monitorObj);
    }
}
