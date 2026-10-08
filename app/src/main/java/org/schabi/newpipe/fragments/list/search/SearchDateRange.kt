package org.schabi.newpipe.fragments.list.search

import java.time.LocalDate
import java.util.Locale

/** Query dates are resolved once per search, so pagination uses the same boundaries. */
object SearchDateRange {
    private val relative = Regex("([1-9][0-9]{0,3})\\s+(day|week|month|year)s?", RegexOption.IGNORE_CASE)

    @JvmStatic
    fun resolve(value: String, today: LocalDate): LocalDate? {
        val text = value.trim()
        if (text.isEmpty()) return null
        val match = relative.matchEntire(text)
        if (match == null) return LocalDate.parse(text)
        val amount = match.groupValues[1].toLong()
        return when (match.groupValues[2].lowercase(Locale.ROOT)) {
            "day" -> today.minusDays(amount)
            "week" -> today.minusWeeks(amount)
            "month" -> today.minusMonths(amount)
            else -> today.minusYears(amount)
        }
    }

    @JvmStatic
    fun query(query: String, after: String, before: String, today: LocalDate): String {
        val start = resolve(after, today)
        val end = resolve(before, today)
        require(start == null || end == null || start < end)
        return buildString {
            append(query.trim())
            if (start != null) append(" after:$start")
            if (end != null) append(" before:$end")
        }.trim()
    }
}
