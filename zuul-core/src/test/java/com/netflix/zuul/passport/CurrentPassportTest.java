package com.netflix.zuul.passport;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class CurrentPassportTest
{
    private static final String SAMPLE_1 = "CurrentPassport {start_ms=0, [+0=IN_REQ_HEADERS_RECEIVED, +268517=FILTERS_INBOUND_START, +707132=IN_REQ_LAST_CONTENT_RECEIVED, +1600917=MISC_IO_START, +3127161=MISC_IO_STOP, +5724232=FILTERS_INBOUND_END, +5940897=ORIGIN_CONN_ACQUIRE_START, +5944850=ORIGIN_CONN_ACQUIRE_END, +6204375=OUT_REQ_HEADERS_SENDING, +6250195=OUT_REQ_LAST_CONTENT_SENDING, +6271590=OUT_REQ_HEADERS_SENT, +6272693=OUT_REQ_LAST_CONTENT_SENT, +340580961=IN_RESP_HEADERS_RECEIVED, +340653170=ORIGIN_CONN_ACQUIRE_START, +340656161=ORIGIN_CONN_ACQUIRE_END, +340934564=OUT_REQ_HEADERS_SENDING, +340984772=OUT_REQ_LAST_CONTENT_SENDING, +341008133=OUT_REQ_HEADERS_SENT, +341009544=OUT_REQ_LAST_CONTENT_SENT, +341025338=IN_RESP_LAST_CONTENT_RECEIVED, +341042653=ORIGIN_CH_CLOSE, +341998593=ORIGIN_CH_INACTIVE, +1094106356=IN_RESP_HEADERS_RECEIVED, +1094258036=FILTERS_OUTBOUND_START, +1117794707=NOW]}";

    @Test
    public void test_findEachPairOf_1pair()
    {
        CurrentPassport passport = CurrentPassport.parseFromToString(
                "CurrentPassport {start_ms=0, [+0=IN_REQ_HEADERS_RECEIVED, +5=FILTERS_INBOUND_START, +50=IN_REQ_LAST_CONTENT_RECEIVED, +200=MISC_IO_START, +250=MISC_IO_STOP, +350=FILTERS_INBOUND_END, +1117794707=NOW]}");

        List<StartAndEnd> pairs = passport.findEachPairOf(PassportState.IN_REQ_HEADERS_RECEIVED, PassportState.IN_REQ_LAST_CONTENT_RECEIVED);
        assertEquals(1, pairs.size());
        assertEquals(0, pairs.get(0).startTime);
        assertEquals(50, pairs.get(0).endTime);
    }

    @Test
    public void test_findEachPairOf_2pairs()
    {
        CurrentPassport passport = CurrentPassport.parseFromToString(
                "CurrentPassport {start_ms=0, [+0=IN_REQ_HEADERS_RECEIVED, +5=FILTERS_INBOUND_START, +50=IN_REQ_LAST_CONTENT_RECEIVED, +200=MISC_IO_START, +250=MISC_IO_STOP, +300=MISC_IO_START, +350=FILTERS_INBOUND_END, +400=MISC_IO_STOP, +1117794707=NOW]}");

        List<StartAndEnd> pairs = passport.findEachPairOf(PassportState.MISC_IO_START, PassportState.MISC_IO_STOP);
        assertEquals(2, pairs.size());
        assertEquals(200, pairs.get(0).startTime);
        assertEquals(250, pairs.get(0).endTime);
        assertEquals(300, pairs.get(1).startTime);
        assertEquals(400, pairs.get(1).endTime);
    }

    @Test
    public void test_findEachPairOf_noneFound()
    {
        CurrentPassport passport = CurrentPassport.parseFromToString(
                "CurrentPassport {start_ms=0, [+0=FILTERS_INBOUND_START, +200=MISC_IO_START, +1117794707=NOW]}");

        List<StartAndEnd> pairs = passport.findEachPairOf(PassportState.IN_REQ_HEADERS_RECEIVED, PassportState.IN_REQ_LAST_CONTENT_RECEIVED);
        assertEquals(0, pairs.size());
    }

    @Test
    public void test_findEachPairOf_endButNoStart()
    {
        CurrentPassport passport = CurrentPassport.parseFromToString(
                "CurrentPassport {start_ms=0, [+0=FILTERS_INBOUND_START, +50=IN_REQ_LAST_CONTENT_RECEIVED, +200=MISC_IO_START, +1117794707=NOW]}");

        List<StartAndEnd> pairs = passport.findEachPairOf(PassportState.IN_REQ_HEADERS_RECEIVED, PassportState.IN_REQ_LAST_CONTENT_RECEIVED);
        assertEquals(0, pairs.size());
    }

    @Test
    public void test_findEachPairOf_wrongOrder()
    {
        CurrentPassport passport = CurrentPassport.parseFromToString(
                "CurrentPassport {start_ms=0, [+0=FILTERS_INBOUND_START, +50=IN_REQ_LAST_CONTENT_RECEIVED, +200=MISC_IO_START, +250=IN_REQ_HEADERS_RECEIVED, +1117794707=NOW]}");

        List<StartAndEnd> pairs = passport.findEachPairOf(PassportState.IN_REQ_HEADERS_RECEIVED, PassportState.IN_REQ_LAST_CONTENT_RECEIVED);
        assertEquals(0, pairs.size());
    }
}
