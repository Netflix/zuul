package com.netflix.zuul;

import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.filters.BaseSyncFilter;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.ZuulMessageImpl;
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

            long avgNs = test.runTest1_Original();
            System.out.println("Original averaged " + avgNs + " ns per run.");

            avgNs = test.runTest1_Experimental();
            System.out.println("Experiment averaged " + avgNs + " ns per run.");
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
        test.runTest1(processor, 100);

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
        test.runTest1(processor, 100);

        // run for real.
        long avgNs = test.runTest1(processor, 10000);

        return avgNs;
    }

    long runTest1(FilterProcessor processor, int testCount)
    {
        ZuulMessage msg = new ZuulMessageImpl(new SessionContext(), new Headers());

        // Process the filters a loop and record time taken.
        long startTime = System.nanoTime();
        for (int i=0; i<testCount; i++) {
            Observable<ZuulMessage> chain = Observable.just(msg);
            chain = processor.applyInboundFilters(chain);
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

    ArrayList<ZuulFilter> createFilters(int count, boolean shouldFilter)
    {
        // Create some inbound and outbound dummy filters.
        ArrayList<ZuulFilter> filters = new ArrayList<>();
        for (int i=0; i<count; i++) {
            filters.add(createFilter("in", i, shouldFilter));
        }
        for (int i=0; i<count; i++) {
            filters.add(createFilter("out", i, shouldFilter));
        }
        return filters;
    }

    ZuulFilter createFilter(String filterType, int index, boolean shouldFilter)
    {
        return new MockSyncFilter("dummyfilter-" + index, filterType, index, shouldFilter);
    }


    class MockSyncFilter extends BaseSyncFilter<ZuulMessage, ZuulMessage>
    {
        private String filterName;
        private String filterType;
        private int filterOrder;
        private boolean shouldFilter;

        public MockSyncFilter(String filterName, String filterType, int filterOrder, boolean shouldFilter)
        {
            this.filterName = filterName;
            this.filterType = filterType;
            this.filterOrder = filterOrder;
            this.shouldFilter = shouldFilter;
        }

        @Override
        public String filterType()
        {
            return filterType;
        }

        @Override
        public String filterName()
        {
            return filterName;
        }

        @Override
        public int filterOrder()
        {
            return filterOrder;
        }

        @Override
        public boolean shouldFilter(ZuulMessage msg)
        {
            return shouldFilter;
        }

        @Override
        public ZuulMessage apply(ZuulMessage input)
        {
//            // Do some work.
//            ArrayList<String> texts = new ArrayList<>();
//            for (int i = 0; i < 1000; i++) {
//                int y = i + 100;
//                String text = "some text - " + y;
//                texts.add(text);
//            }

            return input;
        }
    }
}