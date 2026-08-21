package org.schabi.newpipe.util

import androidx.annotation.IdRes
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.extractor.stream.StreamInfoItem

enum class StreamListFilter(@IdRes val chipId: Int) {
    NONE(0),
    UNWATCHED(R.id.filter_unwatched),
    LIVE(R.id.filter_live),
    SHORTS(R.id.filter_shorts),
    PARTIALLY_WATCHED(R.id.filter_partially_watched);

    companion object {
        const val SHORTS_MAX_DURATION_SECONDS = 180L

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

            LIVE -> StreamTypeUtil.isLiveStream(stream.streamType)

            SHORTS -> isShort(stream)

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

        private fun isShort(stream: StreamInfoItem): Boolean {
            return !StreamTypeUtil.isLiveStream(stream.streamType) &&
                (
                    stream.url.contains("/shorts/") ||
                        stream.duration in 1..SHORTS_MAX_DURATION_SECONDS
                    )
        }
    }
}
