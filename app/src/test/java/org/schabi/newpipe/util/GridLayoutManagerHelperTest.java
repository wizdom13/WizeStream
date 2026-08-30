package org.schabi.newpipe.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GridLayoutManagerHelperTest {
    @Test
    public void measuredContentWidthControlsTheSpanCount() {
        assertEquals(2, GridLayoutManagerHelper.calculateSpanCount(560, 240));
        assertEquals(3, GridLayoutManagerHelper.calculateSpanCount(800, 240));
    }

    @Test
    public void configuredColumnsOverrideAutomaticSizing() {
        assertEquals(2, GridLayoutManagerHelper.calculateSpanCount(1024, 188, 2));
        assertEquals(3, GridLayoutManagerHelper.calculateSpanCount(1024, 188, 3));
        assertEquals(4, GridLayoutManagerHelper.calculateSpanCount(1024, 188, 4));
    }

    @Test
    public void invalidConfiguredColumnsFallBackToAutomaticSizing() {
        assertEquals(5, GridLayoutManagerHelper.calculateSpanCount(1024, 188, 0));
        assertEquals(5, GridLayoutManagerHelper.calculateSpanCount(1024, 188, 1));
        assertEquals(5, GridLayoutManagerHelper.calculateSpanCount(1024, 188, 5));
    }

    @Test
    public void preferenceValuesAreParsedAndValidated() {
        assertEquals(0, GridLayoutManagerHelper.parsePreferredSpanCount(null));
        assertEquals(0, GridLayoutManagerHelper.parsePreferredSpanCount("auto"));
        assertEquals(0, GridLayoutManagerHelper.parsePreferredSpanCount("invalid"));
        assertEquals(0, GridLayoutManagerHelper.parsePreferredSpanCount("1"));
        assertEquals(0, GridLayoutManagerHelper.parsePreferredSpanCount("5"));
        assertEquals(2, GridLayoutManagerHelper.parsePreferredSpanCount("2"));
        assertEquals(3, GridLayoutManagerHelper.parsePreferredSpanCount("3"));
        assertEquals(4, GridLayoutManagerHelper.parsePreferredSpanCount("4"));
    }

    @Test
    public void narrowAndInvalidMeasurementsKeepOneColumn() {
        assertEquals(1, GridLayoutManagerHelper.calculateSpanCount(180, 240));
        assertEquals(1, GridLayoutManagerHelper.calculateSpanCount(0, 240));
        assertEquals(1, GridLayoutManagerHelper.calculateSpanCount(800, 0));
    }
}
