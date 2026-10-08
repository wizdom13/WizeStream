package org.schabi.newpipe.fragments.list.search

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchDateRangeTest {
    @Test
    fun relativeDaysWeeksAndMonthsUseCalendarArithmetic() {
        val today = LocalDate.of(2026, 10, 8)
        assertEquals("lecture after:2026-10-07", SearchDateRange.query("lecture", "1 day", "", today))
        assertEquals("lecture after:2026-09-24", SearchDateRange.query("lecture", "2 weeks", "", today))
        assertEquals("lecture after:2026-08-08", SearchDateRange.query("lecture", "2 months", "", today))
        assertEquals("lecture after:2026-04-08", SearchDateRange.query("lecture", "6 MONTHS", "", today))
    }

    @Test
    fun monthEndAndLeapYearAreClampedToValidDates() {
        assertEquals(LocalDate.of(2024, 2, 29), SearchDateRange.resolve("1 month", LocalDate.of(2024, 3, 31)))
        assertEquals(LocalDate.of(2023, 2, 28), SearchDateRange.resolve("1 year", LocalDate.of(2024, 2, 29)))
    }

    @Test
    fun presetRecalculatesWhenSearchedOnAnotherDay() {
        assertEquals("x after:2026-10-07", SearchDateRange.query("x", "1 day", "", LocalDate.of(2026, 10, 8)))
        assertEquals("x after:2026-10-08", SearchDateRange.query("x", "1 day", "", LocalDate.of(2026, 10, 9)))
    }

    @Test
    fun customRangeAndEmptyRangePreserveQuery() {
        val today = LocalDate.of(2026, 10, 8)
        assertEquals("lecture after:2026-01-01 before:2026-03-01", SearchDateRange.query("lecture", "2026-01-01", "2026-03-01", today))
        assertEquals("lecture", SearchDateRange.query("lecture", "", "", today))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsReversedRange() {
        SearchDateRange.query("x", "2026-10-08", "2026-10-07", LocalDate.now())
    }

    @Test(expected = java.time.DateTimeException::class)
    fun rejectsInvalidDate() {
        SearchDateRange.resolve("2026-02-30", LocalDate.now())
    }
}
