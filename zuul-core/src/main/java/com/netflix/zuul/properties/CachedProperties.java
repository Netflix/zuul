package com.netflix.zuul.properties;

import com.netflix.config.*;

/**
 * Some wrappers around DynamicPropertyS that cache the values and setup updates on callback. This is a performance optimization
 * to avoid the overhead on calling DynamicProperty.get() which was found under when profiling under high load.
 *
 * User: michaels@netflix.com
 * Date: 11/20/15
 * Time: 11:09 AM
 */
public class CachedProperties
{
    public static class Boolean
    {
        private DynamicBooleanProperty fastProperty;
        private volatile boolean value;

        public Boolean(java.lang.String propName, boolean initialValue)
        {
            value = initialValue;
            fastProperty = DynamicPropertyFactory.getInstance().getBooleanProperty(propName, initialValue);

            // Add a callback to update the volatile value when the property is changed.
            fastProperty.addCallback(() -> value = fastProperty.get() );
        }

        public boolean get() {
            return value;
        }
    }

    public static class Int
    {
        private DynamicIntProperty fastProperty;
        private volatile int value;

        public Int(java.lang.String propName, int initialValue)
        {
            value = initialValue;
            fastProperty = DynamicPropertyFactory.getInstance().getIntProperty(propName, initialValue);

            // Add a callback to update the volatile value when the property is changed.
            fastProperty.addCallback(() -> value = fastProperty.get() );
        }

        public int get() {
            return value;
        }
    }

    public static class Long
    {
        private DynamicLongProperty fastProperty;
        private volatile long value;

        public Long(java.lang.String propName, long initialValue)
        {
            value = initialValue;
            fastProperty = DynamicPropertyFactory.getInstance().getLongProperty(propName, initialValue);

            // Add a callback to update the volatile value when the property is changed.
            fastProperty.addCallback(() -> value = fastProperty.get() );
        }

        public long get() {
            return value;
        }
    }

    public static class Double
    {
        private DynamicDoubleProperty fastProperty;
        private volatile double value;

        public Double(java.lang.String propName, double initialValue)
        {
            value = initialValue;
            fastProperty = DynamicPropertyFactory.getInstance().getDoubleProperty(propName, initialValue);

            // Add a callback to update the volatile value when the property is changed.
            fastProperty.addCallback(() -> value = fastProperty.get() );
        }

        public double get() {
            return value;
        }
    }

    public static class String
    {
        private DynamicStringProperty fastProperty;
        private volatile java.lang.String value;

        public String(java.lang.String propName, java.lang.String initialValue)
        {
            value = initialValue;
            fastProperty = DynamicPropertyFactory.getInstance().getStringProperty(propName, initialValue);

            // Add a callback to update the volatile value when the property is changed.
            fastProperty.addCallback(() -> value = fastProperty.get() );
        }

        public java.lang.String get() {
            return value;
        }
    }
}
