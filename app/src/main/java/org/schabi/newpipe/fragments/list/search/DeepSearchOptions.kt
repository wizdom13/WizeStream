package org.schabi.newpipe.fragments.list.search

import kotlin.math.roundToLong

/** User-selected collection limits; result and continuation safeguards remain independent. */
object DeepSearchOptions {
    const val DEFAULT_PAGES = 100
    const val DEFAULT_DELAY_MILLIS = 2000L
    const val MIN_DELAY_MILLIS = 300L
    const val MAX_DELAY_MILLIS = 10000L

    @JvmStatic
    fun parsePages(value: String): Int? = value.trim().toIntOrNull()?.takeIf { it in 1..SearchPageBudget.MAX_PAGES }

    @JvmStatic
    fun parseDelaySeconds(value: String): Long? {
        val seconds = value.trim().replace(',', '.').toDoubleOrNull() ?: return null
        if (!seconds.isFinite() || seconds < 0.3 || seconds > 10) return null
        return (seconds * 1000).roundToLong()
    }
}
