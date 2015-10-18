package com.netflix.zuul.bytebuf;

import io.netty.buffer.ByteBuf;
import org.junit.Test;
import rx.Observable;
import rx.observers.TestSubscriber;

public class ByteBufUtilsTest {

    @Test(timeout = 60000)
    public void testAggregateEmptySource() throws Exception {
        TestSubscriber<ByteBuf> subscriber = new TestSubscriber<>();
        ByteBufUtils.aggregate(Observable.<ByteBuf>empty(), 10).subscribe(subscriber);

        subscriber.awaitTerminalEvent();
        subscriber.assertNoErrors();

        subscriber.assertNoValues();
    }
}