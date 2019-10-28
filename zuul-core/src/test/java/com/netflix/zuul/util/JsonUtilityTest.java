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

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link JsonUtility}.
 */
@RunWith(JUnit4.class)
public class JsonUtilityTest {

    // I'm using LinkedHashMap in the testing so I get consistent ordering for the expected results

    @Test
    public void testSimpleOne() {
        Map<String, Object> jsonData = new LinkedHashMap<String, Object>();
        jsonData.put("myKey", "myValue");

        String json = JsonUtility.jsonFromMap(jsonData);
        String expected = "{\"myKey\":\"myValue\"}";

        assertEquals(expected, json);
    }

    @Test
    public void testSimpleTwo() {
        Map<String, Object> jsonData = new LinkedHashMap<String, Object>();
        jsonData.put("myKey", "myValue");
        jsonData.put("myKey2", "myValue2");

        String json = JsonUtility.jsonFromMap(jsonData);
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

        String json = JsonUtility.jsonFromMap(jsonData);
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

        String json = JsonUtility.jsonFromMap(jsonData);
        String expected = "{\"myKey\":\"myValue\",\"myNestedData\":{\"myNestedKey\":\"myNestedValue\",\"myNestedKey2\":\"myNestedValue2\"}}";

        assertEquals(expected, json);
    }

    @Test
    public void testArrayOne() {
        Map<String, Object> jsonData = new LinkedHashMap<String, Object>();
        int[] numbers = {1, 2, 3, 4};
        jsonData.put("myKey", numbers);

        String json = JsonUtility.jsonFromMap(jsonData);
        String expected = "{\"myKey\":[1,2,3,4]}";

        assertEquals(expected, json);
    }

    @Test
    public void testArrayTwo() {
        Map<String, Object> jsonData = new LinkedHashMap<String, Object>();
        String[] values = {"one", "two", "three", "four"};
        jsonData.put("myKey", values);

        String json = JsonUtility.jsonFromMap(jsonData);
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

        String json = JsonUtility.jsonFromMap(jsonData);
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

        String json = JsonUtility.jsonFromMap(jsonData);
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

        String json = JsonUtility.jsonFromMap(jsonData);
        String expected = "{\"messages\":[{\"a\":\"valueA1\",\"b\":\"valueB1\"},{\"a\":\"valueA2\",\"b\":\"valueB2\"}]}";

        assertEquals(expected, json);
    }
}
