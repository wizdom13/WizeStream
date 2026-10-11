package org.schabi.newpipe.fragments.list.search

import java.time.LocalDate

object MonthlySearchQueries {
    @JvmStatic
    fun forYear(query: String, year: Int): List<String> {
        require(year in 1900..9998)
        val base = query.replace(Regex("(?i)(?<!\\S)(?:after|before):\\S+"), "").trim()
        return (1..12).map { month ->
            val start = LocalDate.of(year, month, 1)
            "$base after:$start before:${start.plusMonths(1)}"
        }
    }

    @JvmStatic
    fun isUploadDateFilter(name: String): Boolean = name in setOf("past_hour", "past_day", "past_week", "past_month", "past_year")
}
