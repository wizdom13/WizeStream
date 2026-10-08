package org.schabi.newpipe.extractor.services.odysee

import com.grack.nanojson.JsonObject
import java.time.DateTimeException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** A claim update timestamp must not replace the original publication date. */
internal fun odyseeUploadDate(claim: JsonObject): OffsetDateTime? {
    val value = claim["value"] as? JsonObject
    val metadata = claim["meta"] as? JsonObject
    for (candidate in listOf(value?.get("release_time"), metadata?.get("creation_timestamp"), claim["creation_timestamp"], claim["timestamp"])) {
        // The SDK serializes protobuf int64 release times as decimal JSON strings.
        val seconds = when (candidate) {
            is Number -> candidate.toLong()
            is String -> candidate.trim().toLongOrNull()
            else -> null
        } ?: continue
        if (seconds <= 0) continue
        try {
            return OffsetDateTime.ofInstant(Instant.ofEpochSecond(seconds), ZoneOffset.UTC)
        } catch (_: DateTimeException) {
            // Invalid optional metadata should not make an otherwise playable claim fail.
        }
    }
    return null
}
