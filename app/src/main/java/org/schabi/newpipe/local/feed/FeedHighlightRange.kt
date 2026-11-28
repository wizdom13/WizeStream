/*
 * Copyright 2026 WizeStream contributors
 * FeedHighlightRange.kt is part of WizeStream
 *
 * License: GPL-3.0+
 */

package org.schabi.newpipe.local.feed

import java.time.OffsetDateTime
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType

internal fun shouldHighlightFeedStream(stream: StreamEntity, updateTime: OffsetDateTime): Boolean {
    if (stream.hasSyntheticUploadDate) return false
    return stream.uploadDate?.isAfter(updateTime)
        ?: (stream.streamType in setOf(StreamType.LIVE_STREAM, StreamType.AUDIO_LIVE_STREAM) || stream.upcomingStatus == true)
}
