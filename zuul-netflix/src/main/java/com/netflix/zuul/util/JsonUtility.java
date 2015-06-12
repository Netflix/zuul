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
package com.netflix.zuul.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * Utility for generating JSON from Maps/Lists
 */
public class JsonUtility {
    private static final Logger logger = LoggerFactory.getLogger(JsonUtility.class);
    /**
     * Pass in a Map and this method will return a JSON string.
     * <p/>
     * The map can contain Objects, int[], Object[] and Collections and they will be converted into string representations.
     * <p/>
     * Nested maps can be included as values and the JSON will have nested object notation.
     * <p/>
     * Arrays/Collections can have Maps in them as well.
     * <p/>
     * See the unit tests for examples.
     *
     * @param jsonData
     * @return
     */
    public static String jsonFromMap(Map<String, Object> jsonData) {
        try {
            JsonDocument json = new JsonDocument();
            json.startGroup();

            for (String key : jsonData.keySet()) {
                Object data = jsonData.get(key);
                if (data instanceof Map) {
                    /* it's a nested map, so we'll recursively add the JSON of this map to the current JSON */
                    json.addValue(key, jsonFromMap((Map<String, Object>) data));
                } else if (data instanceof Object[]) {
                    /* it's an object array, so we'll iterate the elements and put them all in here */
                    json.addValue(key, "[" + stringArrayFromObjectArray((Object[]) data) + "]");
                } else if (data instanceof Collection) {
                    /* it's a collection, so we'll iterate the elements and put them all in here */
                    json.addValue(key, "[" + stringArrayFromObjectArray(((Collection) data).toArray()) + "]");
                } else if (data instanceof int[]) {
                    /* it's an int array, so we'll get the string representation */
                    String intArray = Arrays.toString((int[]) data);
                    /* remove whitespace */
                    intArray = intArray.replaceAll(" ", "");
                    json.addValue(key, intArray);
                } else if (data instanceof JsonCapableObject) {
                    json.addValue(key, jsonFromMap(((JsonCapableObject) data).jsonMap()));
                } else {
                    /* all other objects we assume we are to just put the string value in */
                    json.addValue(key, String.valueOf(data));
                }
            }

            json.endGroup();

            logger.debug("created json from map => " + json.toString());
            return json.toString();
        } catch (Exception e) {
            logger.error("Could not create JSON from Map. ", e);
            return "{}";
        }

    }

    /*
     * return a string like: "one","two","three"
     */
    private static String stringArrayFromObjectArray(Object data[]) {
        StringBuilder arrayAsString = new StringBuilder();
        for (Object o : data) {
            if (arrayAsString.length() > 0) {
                arrayAsString.append(",");
            }
            if (o instanceof Map) {
                arrayAsString.append(jsonFromMap((Map<String, Object>) o));
            } else if (o instanceof JsonCapableObject) {
                arrayAsString.append(jsonFromMap(((JsonCapableObject) o).jsonMap()));
            } else {
                arrayAsString.append("\"").append(String.valueOf(o)).append("\"");
            }
        }
        return arrayAsString.toString();
    }

    private static class JsonDocument {
        StringBuilder json = new StringBuilder();

        private boolean newGroup = false;

        public JsonDocument startGroup() {
            newGroup = true;
            json.append("{");
            return this;
        }

        public JsonDocument endGroup() {
            json.append("}");
            return this;
        }

        public JsonDocument addValue(String key, String value) {
            if (!newGroup) {
                // if this is not the first value in a group, put a comma
                json.append(",");
            }
            /* once we're here, the group is no longer "new" */
            newGroup = false;
            /* append the key/value */
            json.append("\"").append(key).append("\"");
            json.append(":");
            if (value.trim().startsWith("{") || value.trim().startsWith("[")) {
                // the value is either JSON or an array, so we won't wrap with quotes
                json.append(value);
            } else {
                json.append("\"").append(value).append("\"");
            }
            return this;
        }

        public String toString() {
            return json.toString();
        }
    }

    public interface JsonCapableObject {
        Map<String, Object> jsonMap();
    }
}