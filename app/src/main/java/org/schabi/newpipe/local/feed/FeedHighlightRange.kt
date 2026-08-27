/*
 * Copyright 2026 WizeStream contributors
 * FeedHighlightRange.kt is part of WizeStream
 *
 * License: GPL-3.0+
 */

package org.schabi.newpipe.local.feed

internal fun calculateFeedHighlightRebindCount(
    previousHighlightCount: Int,
    currentHighlightCount: Int,
    itemCount: Int
): Int = maxOf(previousHighlightCount, currentHighlightCount).coerceAtMost(itemCount)
