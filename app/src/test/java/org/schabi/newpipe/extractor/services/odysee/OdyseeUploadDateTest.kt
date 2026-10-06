package org.schabi.newpipe.extractor.services.odysee

import com.grack.nanojson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OdyseeUploadDateTest {
    @Test
    fun publicationDateSurvivesLaterClaimUpdates() {
        val claim = JsonObject().apply {
            put("timestamp", 1_790_000_000L)
            put("creation_timestamp", 1_700_000_000L)
            put("value", JsonObject().apply { put("release_time", 1_600_000_000L) })
        }
        assertEquals(1_600_000_000L, odyseeUploadDate(claim)?.toEpochSecond())
        val item = OdyseeStreamInfoItemExtractor(claim)
        assertEquals("2020-09-13T12:26:40Z", item.getTextualUploadDate())
        assertEquals(item.getTextualUploadDate(), item.getUploadDate()?.offsetDateTime()?.toInstant()?.toString())
    }

    @Test
    fun fallsBackToCreationBeforeUpdateTimestamp() {
        val claim = JsonObject().apply {
            put("creation_timestamp", 1_700_000_000L)
            put("timestamp", 1_790_000_000L)
        }
        assertEquals(1_700_000_000L, odyseeUploadDate(claim)?.toEpochSecond())
        claim.remove("creation_timestamp")
        assertEquals(1_790_000_000L, odyseeUploadDate(claim)?.toEpochSecond())
    }

    @Test
    fun invalidPublicationMetadataFallsBackWithoutCrashing() {
        for (release in listOf(null, "invalid", 0L, -1L, Long.MAX_VALUE)) {
            val claim = JsonObject().apply {
                put("value", JsonObject().apply { put("release_time", release) })
                put("creation_timestamp", 1_700_000_000L)
            }
            assertEquals(1_700_000_000L, odyseeUploadDate(claim)?.toEpochSecond())
        }
        assertNull(odyseeUploadDate(JsonObject()))
    }
}
