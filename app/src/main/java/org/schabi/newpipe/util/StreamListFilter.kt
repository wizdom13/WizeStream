package org.schabi.newpipe.util

import androidx.annotation.IdRes
import java.time.OffsetDateTime
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.extractor.stream.StreamInfoItem

enum class StreamListFilter(@IdRes val chipId: Int) {
    NONE(0),
    UNWATCHED(R.id.filter_unwatched),
    LIVE(R.id.filter_live),
    VIDEOS(R.id.filter_videos),
    SHORTS(R.id.filter_shorts),
    UPCOMING(R.id.filter_upcoming),
    PARTIALLY_WATCHED(R.id.filter_partially_watched);

    companion object {
        @JvmStatic
        fun fromChipId(@IdRes chipId: Int): StreamListFilter = entries
            .firstOrNull { it.chipId == chipId } ?: NONE

        @JvmStatic
        fun matches(
            filter: StreamListFilter,
            stream: StreamInfoItem,
            state: StreamStateEntity?
        ): Boolean = when (filter) {
            NONE -> true

            UNWATCHED -> state == null || !state.isValid(stream.duration)

            LIVE -> categoryOf(stream) == LIVE

            VIDEOS -> categoryOf(stream) == VIDEOS

            SHORTS -> categoryOf(stream) == SHORTS

            UPCOMING -> categoryOf(stream) == UPCOMING

            PARTIALLY_WATCHED -> state?.isValid(stream.duration) == true &&
                !state.isFinished(stream.duration)
        }

        @JvmStatic
        fun matches(
            filter: StreamListFilter,
            historyEntry: StreamStatisticsEntry
        ): Boolean = matches(
            filter,
            historyEntry.toStreamInfoItem(),
            StreamStateEntity(historyEntry.streamId, historyEntry.progressMillis)
        )

        @JvmStatic
        fun categoryOf(
            stream: StreamInfoItem,
            now: OffsetDateTime = OffsetDateTime.now()
        ): StreamListFilter = when {
            stream.uploadDate?.offsetDateTime()?.isAfter(now) == true -> UPCOMING
            StreamTypeUtil.isLiveStream(stream.streamType) -> LIVE
            isShort(stream) -> SHORTS
            else -> VIDEOS
        }

        private fun isShort(stream: StreamInfoItem): Boolean {
            return !StreamTypeUtil.isLiveStream(stream.streamType) &&
                (stream.isShortFormContent || stream.url.contains("/shorts/"))
        }
    }
}
