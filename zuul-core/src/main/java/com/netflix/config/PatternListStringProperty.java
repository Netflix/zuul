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

package com.netflix.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * User: michaels@netflix.com
 * Date: 5/15/17
 * Time: 4:38 PM
 */
public class PatternListStringProperty extends DerivedStringProperty<List<Pattern>>
{
    private static final Logger LOG = LoggerFactory.getLogger(PatternListStringProperty.class);

    public PatternListStringProperty(String name, String defaultValue)
    {
        super(name, defaultValue);
    }

    @Override
    protected List<Pattern> derive(String value)
    {
        ArrayList<Pattern> ptns = new ArrayList<>();
        if (value != null) {
            for (String ptnTxt : value.split(",")) {
                try {
                    ptns.add(Pattern.compile(ptnTxt.trim()));
                }
                catch (Exception e) {
                    LOG.error("Error parsing regex pattern list from property! name = " + String.valueOf(this.getName()) + ", value = " + String.valueOf(this.getValue()) + ", pattern = " + String.valueOf(value));
                }
            }
        }
        return ptns;
    }
}
