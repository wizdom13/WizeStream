package org.schabi.newpipe.extractor.services.odysee

import com.grack.nanojson.JsonObject
import java.time.DateTimeException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** A claim update timestamp must not replace the original publication date. */
internal fun odyseeUploadDate(claim: JsonObject): OffsetDateTime? {
    val value = claim["value"] as? JsonObject
    for (candidate in listOf(value?.get("release_time"), claim["creation_timestamp"], claim["timestamp"])) {
        val seconds = (candidate as? Number)?.toLong() ?: continue
        if (seconds <= 0) continue
        try {
            return OffsetDateTime.ofInstant(Instant.ofEpochSecond(seconds), ZoneOffset.UTC)
        } catch (_: DateTimeException) {
            // Invalid optional metadata should not make an otherwise playable claim fail.
        }
    }
    return null
}
