package org.schabi.newpipe.support

import java.net.URI

object CommunityReviewLink {
    fun configuredUrl(value: String): String? {
        if (value.isBlank()) return null
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return null
        return uri.takeIf {
            it.scheme.equals("https", ignoreCase = true) &&
                !it.host.isNullOrBlank() &&
                it.rawUserInfo == null &&
                it.rawQuery == null &&
                it.rawFragment == null
        }?.toASCIIString()
    }
}
