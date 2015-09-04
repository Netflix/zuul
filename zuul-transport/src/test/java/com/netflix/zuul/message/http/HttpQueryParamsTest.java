package com.netflix.zuul.message.http;

import org.junit.Test;

import static org.junit.Assert.*;

public class HttpQueryParamsTest {

    @Test
    public void testMultiples()
    {
        HttpQueryParams qp = new HttpQueryParams();
        qp.add("k1", "v1");
        qp.add("k1", "v2");
        qp.add("k2", "v3");

        assertEquals("k1=v1&k1=v2&k2=v3", qp.toEncodedString());
    }

    @Test
    public void testToEncodedString()
    {
        HttpQueryParams qp = new HttpQueryParams();
        qp.add("k'1", "v1&");
        assertEquals("k%271=v1%26", qp.toEncodedString());

        qp = new HttpQueryParams();
        qp.add("k+", "\n");
        assertEquals("k%2B=%0A", qp.toEncodedString());
    }

    @Test
    public void testToString()
    {
        HttpQueryParams qp = new HttpQueryParams();
        qp.add("k'1", "v1&");
        assertEquals("k'1=v1&", qp.toString());

        qp = new HttpQueryParams();
        qp.add("k+", "\n");
        assertEquals("k+=\n", qp.toString());
    }

    @Test
    public void testEquals()
    {
        HttpQueryParams qp1 = new HttpQueryParams();
        qp1.add("k1", "v1");
        qp1.add("k2", "v2");
        HttpQueryParams qp2 = new HttpQueryParams();
        qp2.add("k1", "v1");
        qp2.add("k2", "v2");

        assertEquals(qp1, qp2);
    }

}