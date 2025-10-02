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

package com.netflix.zuul.passport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CurrentPassportTest {
    @Test
    void test_findEachPairOf_1pair() {
        CurrentPassport passport = CurrentPassport.parseFromToString(
                "CurrentPassport {start_ms=0, [+0=IN_REQ_HEADERS_RECEIVED, +5=FILTERS_INBOUND_START,"
                        + " +50=IN_REQ_LAST_CONTENT_RECEIVED, +200=MISC_IO_START, +250=MISC_IO_STOP,"
                        + " +350=FILTERS_INBOUND_END, +1117794707=NOW]}");

        List<StartAndEnd> pairs = passport.findEachPairOf(
                PassportState.IN_REQ_HEADERS_RECEIVED, PassportState.IN_REQ_LAST_CONTENT_RECEIVED);
        assertThat(pairs.size()).isEqualTo(1);
        assertThat(pairs.get(0).startTime).isEqualTo(0);
        assertThat(pairs.get(0).endTime).isEqualTo(50);
    }

    @Test
    void test_findEachPairOf_2pairs() {
        CurrentPassport passport = CurrentPassport.parseFromToString(
                "CurrentPassport {start_ms=0, [+0=IN_REQ_HEADERS_RECEIVED, +5=FILTERS_INBOUND_START,"
                        + " +50=IN_REQ_LAST_CONTENT_RECEIVED, +200=MISC_IO_START, +250=MISC_IO_STOP, +300=MISC_IO_START,"
                        + " +350=FILTERS_INBOUND_END, +400=MISC_IO_STOP, +1117794707=NOW]}");

        List<StartAndEnd> pairs = passport.findEachPairOf(PassportState.MISC_IO_START, PassportState.MISC_IO_STOP);
        assertThat(pairs.size()).isEqualTo(2);
        assertThat(pairs.get(0).startTime).isEqualTo(200);
        assertThat(pairs.get(0).endTime).isEqualTo(250);
        assertThat(pairs.get(1).startTime).isEqualTo(300);
        assertThat(pairs.get(1).endTime).isEqualTo(400);
    }

    @Test
    void test_findEachPairOf_noneFound() {
        CurrentPassport passport = CurrentPassport.parseFromToString(
                "CurrentPassport {start_ms=0, [+0=FILTERS_INBOUND_START, +200=MISC_IO_START, +1117794707=NOW]}");

        List<StartAndEnd> pairs = passport.findEachPairOf(
                PassportState.IN_REQ_HEADERS_RECEIVED, PassportState.IN_REQ_LAST_CONTENT_RECEIVED);
        assertThat(pairs.size()).isEqualTo(0);
    }

    @Test
    void test_findEachPairOf_endButNoStart() {
        CurrentPassport passport = CurrentPassport.parseFromToString(
                "CurrentPassport {start_ms=0, [+0=FILTERS_INBOUND_START, +50=IN_REQ_LAST_CONTENT_RECEIVED,"
                        + " +200=MISC_IO_START, +1117794707=NOW]}");

        List<StartAndEnd> pairs = passport.findEachPairOf(
                PassportState.IN_REQ_HEADERS_RECEIVED, PassportState.IN_REQ_LAST_CONTENT_RECEIVED);
        assertThat(pairs.size()).isEqualTo(0);
    }

    @Test
    void test_findEachPairOf_wrongOrder() {
        CurrentPassport passport = CurrentPassport.parseFromToString(
                "CurrentPassport {start_ms=0, [+0=FILTERS_INBOUND_START, +50=IN_REQ_LAST_CONTENT_RECEIVED,"
                        + " +200=MISC_IO_START, +250=IN_REQ_HEADERS_RECEIVED, +1117794707=NOW]}");

        List<StartAndEnd> pairs = passport.findEachPairOf(
                PassportState.IN_REQ_HEADERS_RECEIVED, PassportState.IN_REQ_LAST_CONTENT_RECEIVED);
        assertThat(pairs.size()).isEqualTo(0);
    }

    @Test
    void testFindBackwards() {
        CurrentPassport passport = CurrentPassport.parseFromToString(
                "CurrentPassport {start_ms=0, [+0=FILTERS_INBOUND_START, +50=IN_REQ_LAST_CONTENT_RECEIVED,"
                        + " +200=MISC_IO_START, +250=IN_REQ_HEADERS_RECEIVED, +1117794707=NOW]}");

        assertThat(passport.findStateBackwards(PassportState.MISC_IO_START).getTime())
                .isEqualTo(200);
    }

    @Test
    void testGetStateWithNoHistory() {
        assertThat(CurrentPassport.create().getState()).isNull();
    }
}
