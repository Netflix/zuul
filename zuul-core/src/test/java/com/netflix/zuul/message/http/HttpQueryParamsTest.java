/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.zuul.message.http;

import static junit.framework.Assert.assertEquals;


import com.netflix.config.ConfigurationManager;
import com.netflix.zuul.constants.ZuulConstants;
import java.util.Properties;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class HttpQueryParamsTest {
    Properties testProperties = new Properties();

    @Before
    public void setupDefaultResource(){
        setKeepOriginalEncoding(false);
    }

    @Test
    public void testMultiples() {
        HttpQueryParams qp = new HttpQueryParams();
        qp.add("k1", "v1");
        qp.add("k1", "v2");
        qp.add("k2", "v3");

        assertEquals("k1=v1&k1=v2&k2=v3", qp.toEncodedString());
    }

    @Test
    public void testToEncodedString() {
        HttpQueryParams qp = new HttpQueryParams();
        qp.add("k'1", "v1&");
        assertEquals("k%271=v1%26", qp.toEncodedString());

        qp = new HttpQueryParams();
        qp.add("k+", "\n");
        assertEquals("k%2B=%0A", qp.toEncodedString());
    }

    @Test
    public void testToString() {
        HttpQueryParams qp = new HttpQueryParams();
        qp.add("k'1", "v1&");
        assertEquals("k'1=v1&", qp.toString());

        qp = new HttpQueryParams();
        qp.add("k+", "\n");
        assertEquals("k+=\n", qp.toString());
    }

    @Test
    public void testEquals() {
        HttpQueryParams qp1 = new HttpQueryParams();
        qp1.add("k1", "v1");
        qp1.add("k2", "v2");
        HttpQueryParams qp2 = new HttpQueryParams();
        qp2.add("k1", "v1");
        qp2.add("k2", "v2");

        assertEquals(qp1, qp2);
    }

    @Test
    public void testParseKeysWithoutValues() {
        HttpQueryParams expected = new HttpQueryParams();
        expected.add("k1", "");
        expected.add("k2", "v2");
        expected.add("k3", "");

        HttpQueryParams actual = HttpQueryParams.parse("k1=&k2=v2&k3=");

        assertEquals(expected, actual);

        assertEquals("k1=&k2=v2&k3=", actual.toEncodedString());
    }

    @Test
    public void testParseKeyWithoutValueEquals() {
        HttpQueryParams expected = new HttpQueryParams();
        expected.add("k1", "");

        HttpQueryParams actual = HttpQueryParams.parse("k1=");

        assertEquals(expected, actual);

        assertEquals("k1=", actual.toEncodedString());
    }

    @Test
    public void testParseKeyWithoutValue() {
        HttpQueryParams expected = new HttpQueryParams();
        expected.add("k1", "");

        HttpQueryParams actual = HttpQueryParams.parse("k1");

        assertEquals(expected, actual);

        assertEquals("k1", actual.toEncodedString());
    }

    @Test
    public void testParseKeyWithoutValueShort() {
        HttpQueryParams expected = new HttpQueryParams();
        expected.add("=", "");

        HttpQueryParams actual = HttpQueryParams.parse("=");

        assertEquals(expected, actual);

        assertEquals("%3D", actual.toEncodedString());
    }

    @Test
    public void testParseKeysWithoutValuesMixedTrailers() {
        HttpQueryParams expected = new HttpQueryParams();
        expected.add("k1", "");
        expected.add("k2", "v2");
        expected.add("k3", "");
        expected.add("k4", "v4");

        HttpQueryParams actual = HttpQueryParams.parse("k1=&k2=v2&k3&k4=v4");

        assertEquals(expected, actual);

        assertEquals("k1=&k2=v2&k3&k4=v4", actual.toEncodedString());
    }



    @Test
    public void testParseKeysWithoutValuesWithOriginalEncoding()
    {
        setKeepOriginalEncoding(true);
        HttpQueryParams expected = new HttpQueryParams();
        expected.add("k1", "");
        expected.add("k2", "v2%20eq%20value_2");
        expected.add("k3", "");
        expected.add("k4", "v4%20eq%20%27quoted_string%27");
        expected.add("$k5", "key%20eq%20%27quotedString%27");

        HttpQueryParams actual = HttpQueryParams.parse("k1=&k2=v2%20eq%20value_2&k3&k4=v4%20eq%20%27quoted_string%27&$k5=key%20eq%20%27quotedString%27");

        assertEquals(expected, actual);

        assertEquals("k1&k2=v2%20eq%20value_2&k3&k4=v4%20eq%20%27quoted_string%27&$k5=key%20eq%20%27quotedString%27", actual.toEncodedString());
        assertEquals("k1&k2=v2%20eq%20value_2&k3&k4=v4%20eq%20%27quoted_string%27&$k5=key%20eq%20%27quotedString%27", actual.toString());
    }

    private void setKeepOriginalEncoding(boolean keepOriginalEncoding) {
        testProperties.setProperty(ZuulConstants.ZUUL_KEEP_ORIGINAL_QUERY_STRING_ENCODING, String.valueOf(keepOriginalEncoding));
        ConfigurationManager.loadProperties(testProperties);
    }
}
