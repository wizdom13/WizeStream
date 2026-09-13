package org.schabi.newpipe.download

import java.io.Serializable

data class DownloadSegment(val startMs: Long, val endMs: Long) : Serializable {
    init {
        require(startMs >= 0 && endMs > startMs) { "Invalid segment range" }
    }
}

object DownloadSegments {
    @JvmStatic
    fun parseTime(value: String): Long {
        val parts = value.trim().split(':')
        require(parts.size in 1..3 && parts.all { it.matches(Regex("[0-9]{1,6}")) })
        require(parts.drop(1).all { it.toInt() < 60 })
        return parts.fold(0L) { seconds, part -> seconds * 60 + part.toLong() } * 1000
    }

    @JvmStatic
    fun normalize(ranges: List<DownloadSegment>, durationMs: Long): List<DownloadSegment> {
        require(ranges.size <= 100 && durationMs > 0)
        val result = mutableListOf<DownloadSegment>()
        ranges.sortedBy { it.startMs }.forEach { range ->
            require(range.endMs <= durationMs)
            val previous = result.lastOrNull()
            if (previous != null && range.startMs <= previous.endMs) {
                result[result.lastIndex] = DownloadSegment(previous.startMs, maxOf(previous.endMs, range.endMs))
            } else {
                result.add(range)
            }
        }
        return result
    }

    @JvmStatic
    fun encode(ranges: List<DownloadSegment>): String = ranges.joinToString(",") { "${it.startMs}:${it.endMs}" }

    @JvmStatic
    fun decode(value: String): List<DownloadSegment> {
        if (value.isEmpty()) return emptyList()
        require(value.length <= 5000)
        return value.split(',').map {
            val parts = it.split(':')
            require(parts.size == 2)
            DownloadSegment(parts[0].toLong(), parts[1].toLong())
        }
    }
}
