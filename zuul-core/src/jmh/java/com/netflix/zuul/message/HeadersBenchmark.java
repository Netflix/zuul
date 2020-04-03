/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.zuul.message;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;


@State(Scope.Thread)
public class HeadersBenchmark {

    @State(Scope.Thread)
    public static class AddHeaders {
        @Param({"0", "1", "5", "10", "30"})
        public int count;

        @Param({"10"})
        public int nameLength;

        private String[] stringNames;
        private HeaderName[] names;
        private String[] values;

        @Setup
        public void setUp() {
            stringNames = new String[count];
            names = new HeaderName[stringNames.length];
            values = new String[stringNames.length];
            for (int i = 0; i < stringNames.length; i++) {
                UUID uuid = new UUID(ThreadLocalRandom.current().nextLong(), ThreadLocalRandom.current().nextLong());
                String name = uuid.toString();
                assert name.length() >= nameLength;
                name = name.substring(0, nameLength);
                names[i] = new HeaderName(name);
                stringNames[i] = name;
                values[i] = name;
            }
        }

        @Benchmark
        @BenchmarkMode(Mode.AverageTime)
        @OutputTimeUnit(TimeUnit.NANOSECONDS)
        public Headers addHeaders_string() {
            Headers headers = new Headers();
            for (int i = 0; i < count; i++) {
                headers.add(stringNames[i], values[i]);
            }
            return headers;
        }

        @Benchmark
        @BenchmarkMode(Mode.AverageTime)
        @OutputTimeUnit(TimeUnit.NANOSECONDS)
        public Headers addHeaders_headerName() {
            Headers headers = new Headers();
            for (int i = 0; i < count; i++) {
                headers.add(names[i], values[i]);
            }
            return headers;
        }
    }


    @State(Scope.Thread)
    public static class GetSetHeaders {
        @Param({"1", "5", "10", "30"})
        public int count;

        @Param({"10"})
        public int nameLength;

        private String[] stringNames;
        private HeaderName[] names;
        private String[] values;
        Headers headers;

        @Setup
        public void setUp() {
            headers = new Headers();
            stringNames = new String[count];
            names = new HeaderName[stringNames.length];
            values = new String[stringNames.length];
            for (int i = 0; i < stringNames.length; i++) {
                UUID uuid = new UUID(ThreadLocalRandom.current().nextLong(), ThreadLocalRandom.current().nextLong());
                String name = uuid.toString();
                assert name.length() >= nameLength;
                name = name.substring(0, nameLength);
                names[i] = new HeaderName(name);
                stringNames[i] = name;
                values[i] = name;
                headers.add(names[i], values[i]);
            }
        }

        @Benchmark
        @BenchmarkMode(Mode.AverageTime)
        @OutputTimeUnit(TimeUnit.NANOSECONDS)
        public void setHeader_first() {
            headers.set(names[0], "blah");
        }

        @Benchmark
        @BenchmarkMode(Mode.AverageTime)
        @OutputTimeUnit(TimeUnit.NANOSECONDS)
        public void setHeader_last() {
            headers.set(names[count - 1], "blah");
        }

        @Benchmark
        @BenchmarkMode(Mode.AverageTime)
        @OutputTimeUnit(TimeUnit.NANOSECONDS)
        public List<String> getHeader_first() {
            return headers.getAll(names[0]);
        }

        @Benchmark
        @BenchmarkMode(Mode.AverageTime)
        @OutputTimeUnit(TimeUnit.NANOSECONDS)
        public List<String> getHeader_last() {
            return headers.getAll(names[count - 1]);
        }

        @Benchmark
        @BenchmarkMode(Mode.AverageTime)
        @OutputTimeUnit(TimeUnit.NANOSECONDS)
        public void entries(Blackhole blackhole) {
            for (Header header : headers.entries()) {
                blackhole.consume(header);
            }
        }



    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public Headers newHeaders() {
        return new Headers();
    }

}