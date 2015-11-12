package com.netflix.zuul;

import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.http.*;
import rx.Observable;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Basic setup for doing some manual perf testing of Filter processing.
 *
 * User: Mike Smith
 * Date: 10/28/15
 * Time: 11:58 PM
 */
public class FilterProcessorPerfTest
{
    public static void main(String[] args)
    {
        try {
            FilterProcessorPerfTest test = new FilterProcessorPerfTest();

            long avgNs;

            avgNs = test.runTest1_Original();
            System.out.println("Original averaged " + avgNs + " ns per run.");

            avgNs = test.runTest1_Experimental();
            System.out.println("Experiment 1 averaged " + avgNs + " ns per run.");

            avgNs = test.runTest1_Experimental2();
            System.out.println("Experiment 2 averaged " + avgNs + " ns per run.");

        }
        catch(RuntimeException e) {
            e.printStackTrace();
        }
    }

    long runTest1_Original()
    {
        FilterProcessorPerfTest test = new FilterProcessorPerfTest();
        ArrayList<ZuulFilter> filters = test.createFilters(100, false);
        FilterProcessor processor = test.setupProcessor(filters);

        // warmup
        test.runTest1(processor, 1000);

        // run for real.
        long avgNs = test.runTest1(processor, 10000);

        return avgNs;
    }

    long runTest1_Experimental()
    {
        FilterProcessorPerfTest test = new FilterProcessorPerfTest();
        ArrayList<ZuulFilter> filters = test.createFilters(100, false);
        ExperimentalFilterProcessor processor = test.setupProcessor_Experimental(filters);

        // warmup
        test.runTest1(processor, 1000);

        // run for real.
        long avgNs = test.runTest1(processor, 10000);

        return avgNs;
    }

    long runTest1_Experimental2()
    {
        FilterProcessorPerfTest test = new FilterProcessorPerfTest();
        ArrayList<ZuulFilter> filters = test.createFilters(100, false);
        ExperimentalFilterProcessor2 processor = test.setupProcessor_Experimental2(filters);

        // warmup
        test.runTest1(processor, 1000);

        // run for real.
        long avgNs = test.runTest1(processor, 10000);

        return avgNs;
    }

    long runTest1(FilterProcessor processor, int testCount)
    {
        HttpRequestMessageImpl msg = new HttpRequestMessageImpl(new SessionContext(),
                "HTTP/1.1", "get", "/", new HttpQueryParams(), new Headers(), "127.0.0.1", "https", 7001, "localhost");


        // Process the filters a loop and record time taken.
        long startTime = System.nanoTime();
        for (int i=0; i<testCount; i++) {
            Observable<ZuulMessage> chain = processor.applyFilterChain(msg);
            chain.subscribe();
        }
        long duration = System.nanoTime() - startTime;
        long avg = duration / testCount;
        return avg;
    }

    FilterProcessor setupProcessor(Collection<ZuulFilter> filters)
    {
        FilterLoader loader = new FilterLoader();

        for (ZuulFilter filter : filters) {
            loader.putFilter(filter.filterName(), filter, 0);
        }

        return new FilterProcessor(loader, ((filter, status) -> {}));
    }

    ExperimentalFilterProcessor setupProcessor_Experimental(Collection<ZuulFilter> filters)
    {
        FilterLoader loader = new FilterLoader();

        for (ZuulFilter filter : filters) {
            loader.putFilter(filter.filterName(), filter, 0);
        }

        return new ExperimentalFilterProcessor(loader, ((filter, status) -> {}));
    }

    ExperimentalFilterProcessor2 setupProcessor_Experimental2(Collection<ZuulFilter> filters)
    {
        FilterLoader loader = new FilterLoader();

        for (ZuulFilter filter : filters) {
            loader.putFilter(filter.filterName(), filter, 0);
        }

        return new ExperimentalFilterProcessor2(loader, ((filter, status) -> {}));
    }

    ArrayList<ZuulFilter> createFilters(int count, boolean shouldFilter)
    {
        // Create some inbound and outbound dummy filters.
        ArrayList<ZuulFilter> filters = new ArrayList<>();
        for (int i=0; i<count; i++) {
            filters.add(createFilter("in", i, shouldFilter));
        }

        filters.add(createFilter("end", 0, shouldFilter));

        for (int i=0; i<count; i++) {
            filters.add(createFilter("out", i, shouldFilter));
        }
        return filters;
    }

    ZuulFilter createFilter(String filterType, int index, boolean shouldFilter)
    {
        switch(filterType) {
            case "in":
                return new MockHttpInboundFilter(index, shouldFilter);
            case "out":
                return new MockHttpOutboundFilter(index, shouldFilter);
            case "end":
                return new MockEndpointFilter(shouldFilter);
            default:
                return null;
        }
    }
}