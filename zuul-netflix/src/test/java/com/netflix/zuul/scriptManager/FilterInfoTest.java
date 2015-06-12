package com.netflix.zuul.scriptManager;

import org.junit.Test;

import java.util.Date;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class FilterInfoTest {
    @Test
    public void verifyFilterId() {
        FilterInfo filterInfo = new FilterInfo("", "", "", "", "", "", "");
        long originalCreationTime = filterInfo.getCreationDate().getTime();
        filterInfo.getCreationDate().setTime(0);
        assertThat(filterInfo.getCreationDate().getTime(), is(originalCreationTime));
    }

    @Test
    public void creationDateIsCopiedInGetter() {
        FilterInfo filterInfo = new FilterInfo("", "", "", "", "", "", "");
        long originalCreationTime = filterInfo.getCreationDate().getTime();
        filterInfo.getCreationDate().setTime(0);
        assertThat(filterInfo.getCreationDate().getTime(), is(originalCreationTime));
    }

    @Test
    public void creationDateIsCopiedInConstructor() {
        Date date = new Date();
        long originalCreationTime = date.getTime();
        FilterInfo filterInfo =
                new FilterInfo("", 1, date, false, false, "", "", "", "", "", "");
        date.setTime(0);
        assertThat(filterInfo.getCreationDate().getTime(), is(originalCreationTime));
    }
}