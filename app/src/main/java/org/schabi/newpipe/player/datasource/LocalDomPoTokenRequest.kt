package org.schabi.newpipe.player.datasource

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.SharedWebViewRuntime

internal data class LocalDomPoTokenContext(
    val visitorData: String,
    val clientName: String,
    val clientVersion: String,
    val userAgent: String
) {
    val clientId: String
        get() = when (clientName) {
            "WEB" -> "1"
            "MWEB" -> "2"
            "WEB_EMBEDDED_PLAYER" -> "56"
            "ANDROID" -> "3"
            "ANDROID_VR" -> "28"
            "IOS" -> "5"
            "TVHTML5" -> "7"
            else -> throw IllegalArgumentException("Unsupported YouTube client: $clientName")
        }

    val cacheIdentity: String
        get() {
            val digest = MessageDigest.getInstance("SHA-256")
            listOf(clientName, clientVersion, visitorData, userAgent).forEach { value ->
                val bytes = value.toByteArray(StandardCharsets.UTF_8)
                digest.update((bytes.size ushr 24).toByte())
                digest.update((bytes.size ushr 16).toByte())
                digest.update((bytes.size ushr 8).toByte())
                digest.update(bytes.size.toByte())
                digest.update(bytes)
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest())
        }
}

internal fun localDomAttestationContext(
    visitorData: String,
    clientVersion: String
): LocalDomPoTokenContext {
    return LocalDomPoTokenContext(
        visitorData,
        "WEB",
        clientVersion,
        SharedWebViewRuntime.USER_AGENT
    )
}

internal fun buildLocalDomAttestationBody(context: LocalDomPoTokenContext): String {
    return """{"context":{"client":{"clientName":${jsonString(context.clientName)},"clientVersion":${jsonString(context.clientVersion)},"hl":"en","gl":"US","utcOffsetMinutes":0,"visitorData":${jsonString(context.visitorData)}}},"engagementType":"ENGAGEMENT_TYPE_UNBOUND"}"""
}

internal fun buildLocalDomAttestationHeaders(
    context: LocalDomPoTokenContext,
    credentialHeaders: Map<String, List<String>>
): Map<String, List<String>> {
    return HashMap(credentialHeaders).apply {
        put("User-Agent", listOf(context.userAgent))
        put("Accept", listOf("application/json"))
        put("Content-Type", listOf("application/json"))
        put("Origin", listOf("https://www.youtube.com"))
        put("Referer", listOf("https://www.youtube.com/"))
        put("X-Goog-Visitor-Id", listOf(context.visitorData))
        put("X-YouTube-Client-Name", listOf(context.clientId))
        put("X-YouTube-Client-Version", listOf(context.clientVersion))
        put("x-goog-api-key", listOf(BuildConfig.BOTGUARD_GOOGLE_API_KEY))
        put("x-user-agent", listOf("grpc-web-javascript/0.1"))
    }
}

private fun jsonString(value: String): String {
    return buildString(value.length + 2) {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
        append('"')
    }
}
