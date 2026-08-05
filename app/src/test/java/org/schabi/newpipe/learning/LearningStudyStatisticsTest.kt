/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.learning

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class LearningStudyStatisticsTest {
    @Test
    fun calculatesDurationsStreaksAndCalendar() {
        val today = LocalDate.of(2026, 8, 5)
        val statistics = LearningStudyStatistics.from(
            listOf(
                activity("2026-07-29", 5),
                activity("2026-07-30", 10),
                activity("2026-08-02", 15),
                activity("2026-08-03", 20),
                activity("2026-08-04", 25),
                activity("2026-08-05", 30)
            ),
            today
        )

        assertEquals(30L, statistics.todayMillis)
        assertEquals(75L, statistics.weekMillis)
        assertEquals(105L, statistics.allTimeMillis)
        assertEquals(4, statistics.currentStreak)
        assertEquals(4, statistics.longestStreak)
        assertEquals(28, statistics.calendar.size)
        assertEquals(today, statistics.calendar.last().date)
        assertEquals(30L, statistics.calendar.last().watchedDurationMillis)
    }

    @Test
    fun currentStreakRemainsActiveUntilTheEndOfTheNextDay() {
        val statistics = LearningStudyStatistics.from(
            listOf(activity("2026-08-03", 10), activity("2026-08-04", 10)),
            LocalDate.of(2026, 8, 5)
        )

        assertEquals(2, statistics.currentStreak)
        assertEquals(2, statistics.longestStreak)
    }

    private fun activity(date: String, duration: Long) = LearningDailyActivity(date, duration)
}
