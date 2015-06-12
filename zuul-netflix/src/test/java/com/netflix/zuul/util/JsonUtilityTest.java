package com.netflix.zuul.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.netflix.zuul.util.JsonUtility.jsonFromMap;
import static org.junit.Assert.assertEquals;

public class JsonUtilityTest {
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
