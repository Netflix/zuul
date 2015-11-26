package com.netflix.zuul.properties;

import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicPropertyFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * User: Mike Smith
 * Date: 11/25/15
 * Time: 12:34 PM
 */
public class CachedPropertiesPerfTest
{
    public static void main(String[] args)
    {
        try {
//            int threadPoolCount = 300;
//            int taskCount = 500000;
//            int loopCount = 100000;

            int threadPoolCount = 1;
            int taskCount = 50000;
            int loopCount = 100000;

            CachedPropertiesPerfTest test = new CachedPropertiesPerfTest(threadPoolCount);

            ArrayList<Callable<Object>> dynamicPropertyTasks = createDynamicBooleanPropertyTasks(taskCount, loopCount);
            ArrayList<Callable<Object>> cachedPropertyTasks = createCachedBooleanPropertyTasks(taskCount, loopCount);

            // First warmup.
            test.run(dynamicPropertyTasks.subList(0, 50));
            test.run(cachedPropertyTasks.subList(0, 50));


            // Now run benchmark.
            long durationExperiment = test.run(cachedPropertyTasks);
            long durationOriginal = test.run(dynamicPropertyTasks);
            test.shutdown();

            System.out.println("#####################");
            System.out.println("Experiment totalled " + durationExperiment + " ms.");
            System.out.println("Original totalled " + durationOriginal + " ms.");

        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }

    private static ArrayList<Callable<Object>> createDynamicBooleanPropertyTasks(int taskCount, int loopCount)
    {
        DynamicBooleanProperty prop = DynamicPropertyFactory.getInstance()
                .getBooleanProperty("zuul.test.cachedprops.original", true);

        ArrayList<Callable<Object>> tasks = new ArrayList<>();
        for (int i=0; i<taskCount; i++) {
            tasks.add(new DynamicBooleanPropertyTask(prop, loopCount));
        }
        return tasks;
    }

    private static ArrayList<Callable<Object>> createCachedBooleanPropertyTasks(int taskCount, int loopCount)
    {
        CachedProperties.Boolean prop =
                new CachedProperties.Boolean("zuul.test.cachedprops.original", true);

        ArrayList<Callable<Object>> tasks = new ArrayList<>();
        for (int i=0; i<taskCount; i++) {
            tasks.add(new CachedBooleanPropertyTask(prop, loopCount));
        }
        return tasks;
    }


    private ExecutorService service;

    public CachedPropertiesPerfTest(int threadPoolSize)
    {
        service = Executors.newFixedThreadPool(threadPoolSize);
    }

    public long run(Collection<Callable<Object>> tasks) throws Exception
    {
        long startTime = System.currentTimeMillis();

        service.invokeAll(tasks);

        return System.currentTimeMillis() - startTime;
    }

    public void shutdown()
    {
        service.shutdown();
    }


    static class DynamicBooleanPropertyTask implements Callable
    {
        private DynamicBooleanProperty prop;
        private int loopCount;

        public DynamicBooleanPropertyTask(DynamicBooleanProperty prop, int loopCount)
        {
            this.prop = prop;
            this.loopCount = loopCount;
        }

        @Override
        public Object call() throws Exception
        {
            for (int i=0; i<loopCount; i++) {
                prop.get();
            }

            return null;
        }
    }

    static class CachedBooleanPropertyTask implements Callable
    {
        private static int ID_POOL = 1;
        private int id;
        private CachedProperties.Boolean prop;
        private int loopCount;

        public CachedBooleanPropertyTask(CachedProperties.Boolean prop, int loopCount)
        {
            this.id = ID_POOL++;

            this.prop = prop;
            this.loopCount = loopCount;
        }

        @Override
        public Object call() throws Exception
        {
            for (int i=0; i<loopCount; i++) {
                prop.get();
            }

            //System.out.println("Completed " + loopCount + " iterations in task " + id);

            return null;
        }
    }
}
