package org.schabi.newpipe.player.playqueue

import java.net.URI
import java.net.URLDecoder
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

/** Carries an explicit link position independently of extracted or cached stream metadata. */
object TimestampedPlayQueue {
    @JvmStatic
    fun create(info: StreamInfo, requestedUrl: String?): SinglePlayQueue {
        val position = positionMillis(info.serviceId, requestedUrl)
        return if (position == null) SinglePlayQueue(info) else SinglePlayQueue(info, position)
    }

    internal fun positionMillis(serviceId: Int, url: String?): Long? {
        if (serviceId != ServiceList.YouTube.serviceId || url == null) return null
        val uri = try {
            URI(url)
        } catch (_: IllegalArgumentException) {
            return null
        } catch (_: java.net.URISyntaxException) {
            return null
        }
        val parameters = listOfNotNull(uri.rawQuery, uri.rawFragment).flatMap { it.split('&') }
        for (key in listOf("t", "start", "time_continue")) {
            val parameter = parameters.firstOrNull { it.substringBefore('=') == key } ?: continue
            val rawValue = parameter.substringAfter('=', "")
            if (rawValue.length > 64) return null
            val value = try {
                URLDecoder.decode(rawValue, "UTF-8")
            } catch (_: IllegalArgumentException) {
                return null
            }
            val seconds = parseSeconds(value) ?: return null
            return if (seconds <= Long.MAX_VALUE / 1000) seconds * 1000 else null
        }
        return null
    }

    private fun parseSeconds(value: String): Long? {
        if (value.matches(Regex("[0-9]+"))) return value.toLongOrNull()
        val match = Regex("(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?").matchEntire(value) ?: return null
        if (value.isEmpty()) return null
        var total = 0L
        for ((index, multiplier) in listOf(3600L, 60L, 1L).withIndex()) {
            val digits = match.groupValues[index + 1]
            val part = if (digits.isEmpty()) 0L else digits.toLongOrNull() ?: return null
            if (part > (Long.MAX_VALUE - total) / multiplier) return null
            total += part * multiplier
        }
        return total
    }
}
