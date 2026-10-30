package org.schabi.newpipe.extractor.services.youtube.invidious

import java.net.URI

/** Additional media servers explicitly trusted for one configured instance. Never discovered implicitly. */
object InvidiousMediaOrigins {
    const val MAX_MEDIA_ORIGINS = 16

    private data class Policy(val instance: String, val origins: List<String>)

    @Volatile private var policy: Policy? = null

    @JvmStatic
    fun clear() {
        policy = null
    }

    fun parse(text: String): List<String> {
        require(text.length <= 4096) { "Too many media server addresses" }
        val origins = text.lineSequence().map(String::trim).filter(String::isNotEmpty)
            .map(InvidiousBackend::normalizeInstance).distinct().toList()
        require(origins.size <= MAX_MEDIA_ORIGINS) { "At most $MAX_MEDIA_ORIGINS media servers" }
        return origins
    }

    fun configure(instance: String, text: String) {
        policy = try {
            Policy(InvidiousBackend.normalizeInstance(instance), parse(text))
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    @JvmStatic
    fun allows(uri: URI): Boolean {
        if (!InvidiousBackend.isEnabled() || InvidiousBackend.isYoutubeHost(uri.host)) return false
        val current = policy ?: return false
        return try {
            current.instance == InvidiousBackend.getInstance() && current.origins.any { InvidiousBackend.sameOrigin(URI.create(it), uri) }
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    @JvmStatic
    fun stripInstanceCredentials(uri: URI, headers: MutableMap<String, String>) {
        if (allows(uri) && !InvidiousBackend.isInstanceOrigin(uri)) {
            headers.keys.removeAll { it.equals("Authorization", true) || it.equals("Cookie", true) || it.equals("Proxy-Authorization", true) }
        }
    }

    @JvmStatic
    fun blockedMessage(uri: URI): String {
        val origin = try {
            if (uri.host == null) "invalid address" else URI(uri.scheme, null, uri.host, uri.port, null, null, null).toASCIIString()
        } catch (_: Exception) {
            "invalid address"
        }
        return "Invidious media host is not trusted: $origin. Verify the instance operator's Companion address and add it in Content > Trusted Invidious media servers."
    }
}
