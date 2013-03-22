package com.netflix.zuul.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Type representing Netflix device Electronic Serial Number (ESN)
 */
public class NetflixESN {

	public final static String ESN = "esn";
	public static final String X_NETFLIX_ESN = "X-Netflix.esn";
  

    public static String getPrefix(String esn) {
        // using the old way since that's what TrackerHandlerTest wants and I don't know enough about the requirements to make a change
        return extractPrefixBeforeDash(esn);
    }

    /**
     * Grabs the first 10 chars of the ESN. This is the modern approach.
     */
    public static final String extractPrefixByCharCount(String esn, int count) {
        String prefix;
        if (esn.length() < count) {
            prefix = esn;
        } else {
            // if dash is in first 10 chars, strip chars after it
            prefix = extractPrefixBeforeDash(esn.substring(0, count));
        }
        return prefix;
    }

    /**
     * Older approach to grabbing ESN prefixes.
     */
    public static final String extractPrefixBeforeDash(String esn) {
        if (esn == null) {
            return null;
        }
        int nIndex = esn.indexOf("-");
        if (nIndex == -1)
            return esn;
        return esn.substring(0, nIndex);
    }

    public static String getGroupedPrefix(String esn) {
        return extractGroupedPrefix(esn);
    }

    /**
     * Crude grouping of ESN prefixes into a manageable list.
     * <p>
     * <li>NF*</li>
     * <li>WWW</li>
     * <li>Other</li>
     * <p>
     * JIRA API-5141 will likely result in a better solution than this.
     * 
     * @param esn
     * @return
     */
    public static String extractGroupedPrefix(String esn) {
        if (esn == null) {
            return "Other";
        }

        esn = extractPrefixByCharCount(esn, 10);

        if (esn.startsWith("NF") || esn.equals("WWW")) {
            return esn;
        } else {
            // otherwise we return "Other"
            return "Other";
        }
    }

  

    public static class UnitTest {

        @Test
        public void testPrefixGroup1() {
            assertEquals("NFANDROID1", extractGroupedPrefix("NFANDROID1-234sadf"));
            assertEquals("WWW", extractGroupedPrefix("WWW"));
            assertEquals("Other", extractGroupedPrefix("SKDJFHAIURHF"));
            assertEquals("NFAPPL", extractGroupedPrefix("NFAPPL-01-23jdjs"));
        }
    }

}
