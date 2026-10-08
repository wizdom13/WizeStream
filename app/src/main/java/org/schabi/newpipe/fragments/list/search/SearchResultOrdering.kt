package org.schabi.newpipe.fragments.list.search

import kotlin.math.roundToLong
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

object SearchResultOrdering {
    const val SERVICE_ORDER = 0
    const val LONGEST_FIRST = 1
    const val SHORTEST_FIRST = 2

    @JvmStatic
    fun hoursToSeconds(value: String): Long {
        if (value.isBlank()) return 0
        val hours = value.trim().toDouble()
        require(hours.isFinite() && hours >= 0 && hours <= 1_000_000)
        return (hours * 3600).roundToLong()
    }

    @JvmStatic
    fun secondsToHours(value: Long): String = if (value == 0L) "" else (value / 3600.0).toString()

    @JvmStatic
    fun validate(order: Int, minimum: Long, maximum: Long) {
        require(order in SERVICE_ORDER..SHORTEST_FIRST)
        require(minimum >= 0 && maximum >= 0 && (maximum == 0L || minimum <= maximum))
    }

    @JvmStatic
    fun arrange(items: List<InfoItem>, order: Int, minimum: Long, maximum: Long): List<InfoItem> {
        validate(order, minimum, maximum)
        val ranged = minimum > 0 || maximum > 0
        val unique = items.distinctBy { Triple(it.serviceId, it.infoType, it.url) }
        val matches = if (!ranged) {
            unique
        } else {
            unique.filter {
                val duration = knownDuration(it)
                duration != null && duration >= minimum && (maximum == 0L || duration <= maximum)
            }
        }
        if (order == SERVICE_ORDER) return matches
        return matches.sortedWith { first, second ->
            val a = knownDuration(first)
            val b = knownDuration(second)
            when {
                a == null && b == null -> 0
                a == null -> 1
                b == null -> -1
                order == LONGEST_FIRST -> b.compareTo(a)
                else -> a.compareTo(b)
            }
        }
    }

    private fun knownDuration(item: InfoItem): Long? {
        val stream = item as? StreamInfoItem ?: return null
        if (stream.streamType == StreamType.LIVE_STREAM || stream.streamType == StreamType.AUDIO_LIVE_STREAM) return null
        return stream.duration.takeIf { it > 0 }
    }
}
