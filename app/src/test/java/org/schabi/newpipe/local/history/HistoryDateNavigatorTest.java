package org.schabi.newpipe.local.history;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class HistoryDateNavigatorTest {
    private final List<LocalDate> dates = Arrays.asList(
            LocalDate.of(2026, 8, 20),
            LocalDate.of(2025, 12, 10),
            LocalDate.of(2024, 1, 1));

    @Test
    public void findsExactAndClosestDatesInDescendingHistory() {
        assertEquals(1, HistoryDateNavigator.findClosestIndex(
                dates, LocalDate.of(2025, 12, 10)));
        assertEquals(0, HistoryDateNavigator.findClosestIndex(
                dates, LocalDate.of(2027, 1, 1)));
        assertEquals(2, HistoryDateNavigator.findClosestIndex(
                dates, LocalDate.of(2020, 1, 1)));
        assertEquals(1, HistoryDateNavigator.findClosestIndex(
                dates, LocalDate.of(2025, 11, 1)));
        assertEquals(-1, HistoryDateNavigator.findClosestIndex(
                Collections.emptyList(), LocalDate.of(2026, 1, 1)));
    }

    @Test
    public void switchesBetweenMonthAndYearLabelsBasedOnHistorySpan() {
        assertTrue(HistoryDateNavigator.shouldUseMonthLabels(Arrays.asList(
                LocalDate.of(2026, 8, 1), LocalDate.of(2025, 1, 1))));
        assertFalse(HistoryDateNavigator.shouldUseMonthLabels(dates));
        assertEquals("Aug 2026", HistoryDateNavigator.formatLabel(
                LocalDate.of(2026, 8, 1), true, Locale.US));
        assertEquals("2026", HistoryDateNavigator.formatLabel(
                LocalDate.of(2026, 8, 1), false, Locale.US));
    }
}
