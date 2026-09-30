/*
 * Copyright 2026 WizeStream contributors
 * FeedHighlightRange.kt is part of WizeStream
 *
 * License: GPL-3.0+
 */

package org.schabi.newpipe.local.feed

import java.time.OffsetDateTime
import org.schabi.newpipe.database.stream.model.StreamEntity

internal fun feedHighlightPositions(streams: List<StreamEntity>, updateTime: OffsetDateTime): Set<Int> = streams.indices.filterTo(linkedSetOf()) { index ->
    val stream = streams[index]
    !stream.hasSyntheticUploadDate && stream.uploadDate?.isAfter(updateTime) != false
}

internal fun calculateFeedHighlightRebindCount(
    previousHighlightEnd: Int,
    currentHighlightEnd: Int,
    itemCount: Int
): Int = maxOf(previousHighlightEnd, currentHighlightEnd).coerceAtMost(itemCount)
