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
    public void narrowAndInvalidMeasurementsKeepOneColumn() {
        assertEquals(1, GridLayoutManagerHelper.calculateSpanCount(180, 240));
        assertEquals(1, GridLayoutManagerHelper.calculateSpanCount(0, 240));
        assertEquals(1, GridLayoutManagerHelper.calculateSpanCount(800, 0));
    }
}
