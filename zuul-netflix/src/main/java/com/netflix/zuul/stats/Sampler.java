package com.netflix.zuul.stats;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Helper method(s) for sampling requests.
 *
 * @author mhawthorne
 */
public class Sampler {

    private static final Random random = new Random(System.currentTimeMillis());

    /**
     * Default implementation used to abstract specific type of random call made underneath.
     */
    public static final boolean shouldSample(int percentage) {
        return shouldSampleRandomly(percentage);
    }

    /**
     * For each invocation of this method, there is a <code>percentage</code> chance that this method will return
     * <code>true</code>
     *
     * @param percentage should be a number between 0 and 100
     */
    public static final boolean shouldSampleRandomly(int percentage) {
        if (percentage <= 0) return false;
        else if (percentage >= 100) return true;

        return shouldSampleForBase(percentage, random.nextInt(100) % 100);
    }

    /**
     * For a given millisecond, there is a <code>percentage</code> chance that this method will return
     * <code>true</code>
     *
     * @param percentage should be a number between 0 and 100
     */
    public static final boolean shouldSampleForCurrentMillisecond(int percentage) {
        if (percentage <= 0) return false;
        else if (percentage >= 100) return true;

        return shouldSampleForBase(percentage, System.currentTimeMillis() % 100);
    }

    private static final boolean shouldSampleForBase(int percentage, long base) {
        return percentage <= 50 ? (base % (100 / percentage)) == 0 : (base % (100 / (100 - percentage))) != 0;
    }

    public static final class UnitTest {

        private static final int countSamples(int percentage) throws InterruptedException {
            final int iterations = 1000;
            int sampleCount = 0;
            for(int i = 0; i<iterations; i++) {
                if(Sampler.shouldSample(percentage)) sampleCount++;
            }
            System.out.println(String.format("percentage:%d, iterations:%d, samples:%d", percentage, iterations, sampleCount));

            // interpolates sample count for 100 iterations
            return Math.round(sampleCount / (iterations / 100));
        }

        private static final void assertWithinRange(int num, int min, int max) {
            System.out.println(String.format("checking if %d is between %d and %d", num, min, max));
            assertTrue(String.format("%d is not greater than %d", num, min), num >= min);
            assertTrue(String.format("%d is not less than %d", num, max), num <= max);
//            System.out.println(String.format("%d is between %d and %d", num, min, max));
        }

        private static final void assertWithinDeviation(int expected, int actual, int percentDeviation) {
            final int min = Math.round(expected - (expected * percentDeviation/100f));
            final int max = Math.round(expected + (expected * percentDeviation/100f));
            assertWithinRange(actual, min, max);
        }

        @Test
        public void test0() throws InterruptedException {
            assertEquals(0, countSamples(0));
        }

        @Test
        public void test1() throws InterruptedException {
            assertWithinRange(countSamples(1), 0, 5);
        }

        @Test
        public void test25() throws InterruptedException {
            assertWithinDeviation(25, countSamples(25), 10);
        }

        @Test
        public void test50() throws InterruptedException {
            assertWithinDeviation(50, countSamples(50), 10);
        }

        @Test
        public void test75() throws InterruptedException {
            assertWithinDeviation(75, countSamples(75), 10);
        }

        @Test
        public void test100() throws InterruptedException {
            assertWithinDeviation(100, countSamples(100), 10);
        }

        @Test
        public void testSequence() throws InterruptedException {
            // I had a bug in shouldSampleForCurrentMillisecond where, for certain combinations of percentages,
            // one of the sampling rates observed in prod would not match the configured percentage.

            // this test verifies that 2 sampling percentages conducted in sequence result in 2 correct sample counts,
            // given some space for slight deviations

            final int iterations = 100;
            final int percent1 = 50;
            final int percent2 = 50;
            final int deviationPercent = 30;

            int sample1Count = 0;
            int sample2Count = 0;

            for(int i=0; i<iterations; i++) {
                if(Sampler.shouldSample(percent1)) {
                    sample1Count++;
                    Thread.sleep(1);
                }

                if(Sampler.shouldSample(percent2)) sample2Count++;

                Thread.sleep(1);
            }

            System.out.println(String.format("percent1:%d, percent2:%d, iterations:%d, sample1Count:%d, sample2Count:%d",
                percent1, percent2, iterations, sample1Count, sample2Count));

            final int expectedSampleCount = Math.round(iterations - (iterations * (percent1/100f)));
            final int deviation = Math.round(expectedSampleCount * deviationPercent/100f);
            final int lowerBound = expectedSampleCount - deviation;
            final int upperBound = expectedSampleCount + deviation;

            assertWithinDeviation(percent1, sample1Count, deviationPercent);
            assertWithinDeviation(percent2, sample1Count, deviationPercent);
        }

    }

}
