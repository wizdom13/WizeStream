/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.learning

import androidx.room.ColumnInfo
import androidx.room.Embedded
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import org.schabi.newpipe.database.stream.model.StreamEntity

data class LearningPlaylistSummary(
    @ColumnInfo(name = "playlist_id")
    val playlistId: Long?,
    @ColumnInfo(name = "playlist_name")
    val playlistName: String?,
    @ColumnInfo(name = "thumbnail_url")
    val thumbnailUrl: String?,
    @ColumnInfo(name = "eligible_count")
    val eligibleCount: Int,
    @ColumnInfo(name = "completed_count")
    val completedCount: Int,
    @ColumnInfo(name = "service_id")
    val serviceId: Int? = null,
    @ColumnInfo(name = "playlist_url")
    val playlistUrl: String? = null
) {
    val percentage: Int
        get() = if (eligibleCount == 0) 0 else completedCount * 100 / eligibleCount

    val isCompleted: Boolean
        get() = eligibleCount > 0 && completedCount == eligibleCount
}

data class LearningDashboardStream(
    @Embedded
    val stream: StreamEntity,
    @ColumnInfo(name = "progress_millis")
    val progressMillis: Long,
    @ColumnInfo(name = "note_count")
    val noteCount: Int = 0,
    @ColumnInfo(name = "latest_note_update")
    val latestNoteUpdate: Long = 0
) {
    val progressPercentage: Int
        get() = if (stream.duration <= 0) {
            0
        } else {
            (progressMillis * 100 / (stream.duration * 1_000)).toInt().coerceIn(0, 100)
        }
}

data class LearningDailyActivity(
    @ColumnInfo(name = "local_date")
    val localDate: String,
    @ColumnInfo(name = "watched_duration_ms")
    val watchedDurationMillis: Long
)

data class LearningStudyStatistics(
    val todayMillis: Long,
    val weekMillis: Long,
    val allTimeMillis: Long,
    val currentStreak: Int,
    val longestStreak: Int,
    val calendar: List<LearningCalendarDay>
) {
    companion object {
        const val CALENDAR_DAYS = 28

        fun from(
            dailyActivity: List<LearningDailyActivity>,
            today: LocalDate = LocalDate.now()
        ): LearningStudyStatistics {
            val totals = dailyActivity.associate { LocalDate.parse(it.localDate) to it.watchedDurationMillis }
            val activeDates = totals.filterValues { it > 0 }.keys.sorted()
            val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val currentAnchor = if ((totals[today] ?: 0) > 0) today else today.minusDays(1)

            var currentStreak = 0
            var cursor = currentAnchor
            while ((totals[cursor] ?: 0) > 0) {
                currentStreak++
                cursor = cursor.minusDays(1)
            }

            var longestStreak = 0
            var runningStreak = 0
            var previous: LocalDate? = null
            activeDates.forEach { date ->
                runningStreak = if (previous?.plusDays(1) == date) runningStreak + 1 else 1
                longestStreak = maxOf(longestStreak, runningStreak)
                previous = date
            }

            return LearningStudyStatistics(
                todayMillis = totals[today] ?: 0,
                weekMillis = totals.filterKeys { !it.isBefore(weekStart) && !it.isAfter(today) }
                    .values.sum(),
                allTimeMillis = totals.values.sum(),
                currentStreak = currentStreak,
                longestStreak = longestStreak,
                calendar = (CALENDAR_DAYS - 1 downTo 0).map { daysAgo ->
                    val date = today.minusDays(daysAgo.toLong())
                    LearningCalendarDay(date, totals[date] ?: 0)
                }
            )
        }
    }
}

data class LearningCalendarDay(
    val date: LocalDate,
    val watchedDurationMillis: Long
)

data class LearningDashboardSnapshot(
    val playlists: List<LearningPlaylistSummary>,
    val learningContent: List<LearningDashboardStream>,
    val continueLearning: List<LearningDashboardStream>,
    val recentlyAnnotated: List<LearningDashboardStream>,
    val studyStatistics: LearningStudyStatistics = LearningStudyStatistics.from(emptyList())
) {
    val activePlaylists: List<LearningPlaylistSummary>
        get() = playlists.filter { it.eligibleCount > 0 && !it.isCompleted }

    val completedPlaylists: List<LearningPlaylistSummary>
        get() = playlists.filter(LearningPlaylistSummary::isCompleted)

    val completedStreams: Int
        get() = playlists.sumOf(LearningPlaylistSummary::completedCount)

    val eligibleStreams: Int
        get() = playlists.sumOf(LearningPlaylistSummary::eligibleCount)

    val overallPercentage: Int
        get() = if (eligibleStreams == 0) 0 else completedStreams * 100 / eligibleStreams

    val isEmpty: Boolean
        get() = playlists.none { it.eligibleCount > 0 } &&
            learningContent.isEmpty() && continueLearning.isEmpty() &&
            recentlyAnnotated.isEmpty() &&
            studyStatistics.allTimeMillis == 0L
}
