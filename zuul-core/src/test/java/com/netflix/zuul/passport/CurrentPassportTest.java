package com.netflix.zuul.passport;

import org.junit.Test;

import java.util.List;

import static com.netflix.zuul.passport.PassportState.MISC_IO_START;
import static org.junit.Assert.assertEquals;

public class CurrentPassportTest
{
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

        List<StartAndEnd> pairs = passport.findEachPairOf(MISC_IO_START, PassportState.MISC_IO_STOP);
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

    @Test
    public void testFindBackwards()
    {
        CurrentPassport passport = CurrentPassport.parseFromToString(
                "CurrentPassport {start_ms=0, [+0=FILTERS_INBOUND_START, +50=IN_REQ_LAST_CONTENT_RECEIVED, +200=MISC_IO_START, +250=IN_REQ_HEADERS_RECEIVED, +1117794707=NOW]}");

        assertEquals(200, passport.findStateBackwards(MISC_IO_START).getTime());
    }
}
