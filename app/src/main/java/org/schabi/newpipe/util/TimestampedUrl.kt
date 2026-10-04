package org.schabi.newpipe.util

import org.schabi.newpipe.extractor.ServiceList

object TimestampedUrl {
    @JvmStatic
    fun atPosition(url: String, serviceId: Int, positionMillis: Long, live: Boolean): String {
        if (serviceId != ServiceList.YouTube.serviceId || live || positionMillis < 0) return url
        val base = url.substringBefore('#').substringBefore('?')
        val query = url.substringBefore('#').substringAfter('?', "").split('&')
            .filter { it.isNotBlank() && it.substringBefore('=') !in setOf("t", "start", "time_continue") }
        val fragment = url.substringAfter('#', "").takeIf { it.isNotEmpty() }?.let { "#$it" }.orEmpty()
        return base + "?" + (query + "t=${positionMillis / 1000}").joinToString("&") + fragment
    }
}
