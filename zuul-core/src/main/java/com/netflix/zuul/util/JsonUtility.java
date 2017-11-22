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
package com.netflix.zuul.util;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.junit.Assert.assertEquals;

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
                // the value is either JSON or an array, so we won't aggregate with quotes
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

    public static interface JsonCapableObject {

        public Map<String, Object> jsonMap();

    }

    public static class UnitTest {

        // I'm using LinkedHashMap in the testing so I get consistent ordering for the expected results

        @Test
        public void testSimpleOne() {
            Map<String, Object> jsonData = new LinkedHashMap<String, Object>();
            jsonData.put("myKey", "myValue");

            String json = jsonFromMap(jsonData);
            String expected = "{\"myKey\":\"myValue\"}";

            assertEquals(expected, json);
        }

        @Test
        public void testSimpleTwo() {
            Map<String, Object> jsonData = new LinkedHashMap<String, Object>();
            jsonData.put("myKey", "myValue");
            jsonData.put("myKey2", "myValue2");

            String json = jsonFromMap(jsonData);
            String expected = "{\"myKey\":\"myValue\",\"myKey2\":\"myValue2\"}";

            assertEquals(expected, json);
        }

        @Test
        public void testNestedMapOne() {
            Map<String, Object> jsonData = new LinkedHashMap<String, Object>();
            jsonData.put("myKey", "myValue");

            Map<String, Object> jsonData2 = new LinkedHashMap<String, Object>();
            jsonData2.put("myNestedKey", "myNestedValue");

            jsonData.put("myNestedData", jsonData2);

            String json = jsonFromMap(jsonData);
            String expected = "{\"myKey\":\"myValue\",\"myNestedData\":{\"myNestedKey\":\"myNestedValue\"}}";

            assertEquals(expected, json);
        }

        @Test
        public void testNestedMapTwo() {
            Map<String, Object> jsonData = new LinkedHashMap<String, Object>();
            jsonData.put("myKey", "myValue");

            Map<String, Object> jsonData2 = new LinkedHashMap<String, Object>();
            jsonData2.put("myNestedKey", "myNestedValue");
            jsonData2.put("myNestedKey2", "myNestedValue2");

            jsonData.put("myNestedData", jsonData2);

            String json = jsonFromMap(jsonData);
            String expected = "{\"myKey\":\"myValue\",\"myNestedData\":{\"myNestedKey\":\"myNestedValue\",\"myNestedKey2\":\"myNestedValue2\"}}";

            assertEquals(expected, json);
        }

        @Test
        public void testArrayOne() {
            Map<String, Object> jsonData = new LinkedHashMap<String, Object>();
            int[] numbers = {1, 2, 3, 4};
            jsonData.put("myKey", numbers);

            String json = jsonFromMap(jsonData);
            String expected = "{\"myKey\":[1,2,3,4]}";

            assertEquals(expected, json);
        }

        @Test
        public void testArrayTwo() {
            Map<String, Object> jsonData = new LinkedHashMap<String, Object>();
            String[] values = {"one", "two", "three", "four"};
            jsonData.put("myKey", values);

            String json = jsonFromMap(jsonData);
            String expected = "{\"myKey\":[\"one\",\"two\",\"three\",\"four\"]}";

            assertEquals(expected, json);
        }

        @Test
        public void testCollectionOne() {
            Map<String, Object> jsonData = new LinkedHashMap<String, Object>();
            ArrayList<String> values = new ArrayList<String>();
            values.add("one");
            values.add("two");
            values.add("three");
            values.add("four");
            jsonData.put("myKey", values);

            String json = jsonFromMap(jsonData);
            String expected = "{\"myKey\":[\"one\",\"two\",\"three\",\"four\"]}";

            assertEquals(expected, json);
        }

        @Test
        public void testMapAndList() {
            Map<String, Object> jsonData = new LinkedHashMap<String, Object>();
            jsonData.put("myKey", "myValue");
            int[] numbers = {1, 2, 3, 4};
            jsonData.put("myNumbers", numbers);

            Map<String, Object> jsonData2 = new LinkedHashMap<String, Object>();
            jsonData2.put("myNestedKey", "myNestedValue");
            jsonData2.put("myNestedKey2", "myNestedValue2");
            String[] values = {"one", "two", "three", "four"};
            jsonData2.put("myStringNumbers", values);

            jsonData.put("myNestedData", jsonData2);

            String json = jsonFromMap(jsonData);
            String expected = "{\"myKey\":\"myValue\",\"myNumbers\":[1,2,3,4],\"myNestedData\":{\"myNestedKey\":\"myNestedValue\",\"myNestedKey2\":\"myNestedValue2\",\"myStringNumbers\":[\"one\",\"two\",\"three\",\"four\"]}}";

            assertEquals(expected, json);
        }

        @Test
        public void testArrayOfMaps() {
            Map<String, Object> jsonData = new LinkedHashMap<String, Object>();
            ArrayList<Map<String, Object>> messages = new ArrayList<Map<String, Object>>();

            Map<String, Object> message1 = new LinkedHashMap<String, Object>();
            message1.put("a", "valueA1");
            message1.put("b", "valueB1");
            messages.add(message1);

            Map<String, Object> message2 = new LinkedHashMap<String, Object>();
            message2.put("a", "valueA2");
            message2.put("b", "valueB2");
            messages.add(message2);

            jsonData.put("messages", messages);

            String json = jsonFromMap(jsonData);
            String expected = "{\"messages\":[{\"a\":\"valueA1\",\"b\":\"valueB1\"},{\"a\":\"valueA2\",\"b\":\"valueB2\"}]}";

            assertEquals(expected, json);

        }

    }

}