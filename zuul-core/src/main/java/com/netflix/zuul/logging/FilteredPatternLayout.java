

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

package com.netflix.zuul.logging;

import org.apache.log4j.PatternLayout;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;


/**
 * A modified copy of <a href="https://svn.code.sf.net/p/openutils/code/trunk/openutils-log4j/src/main/java/it/openutils/log4j/FilteredPatternLayout.java">FilteredPatternLayout</a>.
 *
 * An extension of <code>org.apache.log4j.PatternLayout</code> which strips out from stack traces a list of configured
 * entries. Sample configuration:
 *
 * <pre>
 *  &lt;appender name="console" class="org.apache.log4j.ConsoleAppender">
 *      &lt;layout class="it.openutils.log4j.FilteredPatternLayout">
 *          &lt;param name="ConversionPattern" value="%-5p  %c %F(%M:%L) %d{dd.MM.yyyy HH:mm:ss}  %m%n" />
 *          &lt;param name="Filter" value="org.apache.catalina" />
 *          &lt;param name="Filter" value="sun.reflect" />
 *          &lt;param name="Filter" value="javax.servlet.http" />
 *      &lt;/layout>
 *  &lt;/appender>
 * </pre>
 *
 * @author Fabrizio Giustina
 * @author Michael Smith
 *
 */
public class FilteredPatternLayout extends PatternLayout
{

    /**
     * Holds the list of filtered frames.
     */
    private Set<String> filteredFrames = new HashSet<String>();

    private String header;

    private String footer;

    /**
     * Line separator for stacktrace frames.
     */
    private static String lineSeparator = "\n";

    static
    {
        try
        {
            lineSeparator = System.getProperty("line.separator");
        }
        catch (SecurityException ex)
        {
            // ignore
        }
    }

    private static final String FILTERED_LINE_INDICATOR = "\t... filtered lines = ";


    /**
     * Returns the header.
     * @return the header
     */
    @Override
    public String getHeader()
    {
        return header;
    }

    /**
     * Sets the header.
     * @param header the header to set
     */
    public void setHeader(String header)
    {
        this.header = header;
    }

    /**
     * Returns the footer.
     * @return the footer
     */
    @Override
    public String getFooter()
    {
        return footer;
    }

    /**
     * Sets the footer.
     * @param footer the footer to set
     */
    public void setFooter(String footer)
    {
        this.footer = footer;
    }

    /**
     * @see org.apache.log4j.Layout#ignoresThrowable()
     */
    @Override
    public boolean ignoresThrowable()
    {
        return false;
    }

    /**
     * @see PatternLayout#format(LoggingEvent)
     */
    @Override
    public String format(LoggingEvent event)
    {
        String result = super.format(event);

        ThrowableInformation throwableInformation = event.getThrowableInformation();

        if (throwableInformation != null)
        {
            result += getFilteredStacktrace(throwableInformation);
        }

        return result;
    }

    /**
     * Adds new filtered frames. Any stack frame starting with <code>"at "</code> + <code>filter</code> will not be
     * written to the log.
     * @param filters a comma-delimited list of class names or package names to be filtered
     */
    public void setFilters(String filters)
    {
        for (String filter : filters.split(",")) {
            filteredFrames.add("at " + filter.trim());
        }
    }


    private String getFilteredStacktrace(ThrowableInformation throwableInformation)
    {
        StringBuffer buffer = new StringBuffer();

        String[] s = throwableInformation.getThrowableStrRep();

        boolean previousLineWasAMatch = false;
        int consecutiveFilteredCount = 0;
        for (int j = 0; j < s.length; j++)
        {
            String string = s[j];
            boolean shouldAppend = true;

            if (startsWithAFilteredPAttern(string)) {
                shouldAppend = false;
                previousLineWasAMatch = true;
                consecutiveFilteredCount++;
            }
            else {
                appendFilteredLineIndicator(buffer, previousLineWasAMatch, consecutiveFilteredCount);
                consecutiveFilteredCount = 0;
                previousLineWasAMatch = false;
            }

            if (shouldAppend) {
                buffer.append(string);
                buffer.append(lineSeparator);
            }
        }

        // In case consecutive filtered lines run to end of trace.
        appendFilteredLineIndicator(buffer, previousLineWasAMatch, consecutiveFilteredCount);

        return buffer.toString();
    }

    private void appendFilteredLineIndicator(StringBuffer buffer, boolean previousLineWasAMatch, int consecutiveFilteredCount)
    {
        // For the last consecutive filtered line, append some indication that lines have been filtered.
        if (previousLineWasAMatch) {
            buffer.append(FILTERED_LINE_INDICATOR).append(consecutiveFilteredCount);
            buffer.append(lineSeparator);
        }
    }

    /**
     * Check if the given string starts with any of the filtered patterns.
     * @param string checked String
     * @return <code>true</code> if the begininning of the string matches a filtered pattern, <code>false</code>
     * otherwise
     */
    private boolean startsWithAFilteredPAttern(String string)
    {
        Iterator<String> iterator = filteredFrames.iterator();
        while (iterator.hasNext())
        {
            if (string.trim().startsWith(iterator.next()))
            {
                return true;
            }
        }
        return false;
    }

}