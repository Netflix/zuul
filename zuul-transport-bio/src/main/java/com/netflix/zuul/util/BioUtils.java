package com.netflix.zuul.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import rx.Observable;
import rx.observables.StringObservable;

import java.io.InputStream;

public class BioUtils {

    public static Observable<ByteBuf> fromInputStream(InputStream input)
    {
        return StringObservable.from(input)
                               .map(Unpooled::wrappedBuffer)
                               .defaultIfEmpty(Unpooled.buffer());
    }
}
