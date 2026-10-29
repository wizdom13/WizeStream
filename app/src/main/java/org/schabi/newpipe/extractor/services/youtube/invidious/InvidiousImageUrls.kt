package org.schabi.newpipe.extractor.services.youtube.invidious

import java.net.URI

object InvidiousImageUrls {
    @JvmStatic
    fun thumbnailPath(path: String): String {
        // Invidious proxies JPEG thumbnails at /vi/:id/:name, not /vi_webp/.
        val jpegPath = if (path.startsWith("/vi_webp/") && path.endsWith(".webp")) {
            path.replaceFirst("/vi_webp/", "/vi/").removeSuffix(".webp") + ".jpg"
        } else {
            path
        }
        // This endpoint probes available resolutions when maxresdefault is absent.
        return if (jpegPath.matches(Regex("/vi/[A-Za-z0-9_-]+/maxresdefault\\.jpg"))) {
            jpegPath.removeSuffix("maxresdefault.jpg") + "maxres.jpg"
        } else {
            jpegPath
        }
    }

    @JvmStatic
    fun resolve(value: String?, base: String): String {
        if (value.isNullOrBlank()) return ""
        return try {
            val routed = InvidiousBackend.routeImage(value.trim(), base)
            val uri = URI.create("$base/").resolve(routed)
            val sameInstance = InvidiousBackend.sameOrigin(URI.create(base), uri)
            val trusted = base == InvidiousBackend.getInstance() && InvidiousMediaOrigins.allows(uri)
            if (!sameInstance && !trusted) return ""
            // Normalize instance-local API thumbnails as well as direct YouTube image URLs.
            if (sameInstance) {
                val path = thumbnailPath(uri.rawPath)
                if (path != uri.rawPath) {
                    return base + path + (uri.rawQuery?.let { "?$it" } ?: "")
                }
            }
            uri.toString()
        } catch (_: IllegalArgumentException) {
            ""
        }
    }
}
