package com.netflix.api.proxy;

import javax.servlet.ServletInputStream;
import java.io.IOException;

/**
* @author mhawthorne
*/
public class ServletInputStreamWrapper extends ServletInputStream {

    private byte[] data;
    private int idx = 0;

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
