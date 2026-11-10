/*
 * Copyright 2026 WizeStream contributors
 * FeedHighlightRange.kt is part of WizeStream
 *
 * License: GPL-3.0+
 */

package org.schabi.newpipe.local.feed

import java.time.OffsetDateTime
import org.schabi.newpipe.database.stream.model.StreamEntity

internal fun shouldHighlightFeedStream(stream: StreamEntity, updateTime: OffsetDateTime): Boolean = !stream.hasSyntheticUploadDate && stream.uploadDate?.isAfter(updateTime) != false
