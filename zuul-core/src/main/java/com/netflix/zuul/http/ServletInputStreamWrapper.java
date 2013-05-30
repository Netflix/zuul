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
package com.netflix.zuul.http;

import javax.servlet.ServletInputStream;
import java.io.IOException;

/**
 * ServletInputStream wrapper to wrap a byte[] into a ServletInputStream
 *
 * @author mhawthorne
 */
public class ServletInputStreamWrapper extends ServletInputStream {

    private byte[] data;
    private int idx = 0;

    /**
     * Creates a new <code>ServletInputStreamWrapper</code> instance.
     *
     * @param data a <code>byte[]</code> value
     */
    public ServletInputStreamWrapper(byte[] data) {
        if (data == null)
            data = new byte[0];
        this.data = data;
    }

    @Override
    public int read() throws IOException {
        if (idx == data.length)
            return -1;
        // I have to AND the byte with 0xff in order to ensure that it is returned as an unsigned integer
        // the lack of this was causing a weird bug when manually unzipping gzipped request bodies
        return data[idx++] & 0xff;
    }

}
