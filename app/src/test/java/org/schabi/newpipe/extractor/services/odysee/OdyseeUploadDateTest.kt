package org.schabi.newpipe.extractor.services.odysee

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OdyseeUploadDateTest {
    @Test
    fun sdkStringPublicationDatesPreserveTheReportedVideosOriginalDates() {
        // Metadata shape and timestamps returned by resolve for the four videos in #879.
        val dates = listOf(
            "1790812800" to 1_791_248_415L,
            "1790985600" to 1_791_247_461L,
            "1791072000" to 1_791_247_422L,
            "1791158400" to 1_791_247_931L
        )
        for ((release, creation) in dates) {
            val claim = JsonParser.`object`().from(
                """{"value":{"release_time":"$release"},"meta":{"creation_timestamp":$creation},"timestamp":$creation}"""
            )
            assertEquals(release.toLong(), odyseeUploadDate(claim)?.toEpochSecond())
            val item = OdyseeStreamInfoItemExtractor(claim)
            assertEquals(release.toLong(), item.getUploadDate()?.offsetDateTime()?.toEpochSecond())
            assertEquals(odyseeUploadDate(claim)?.toInstant()?.toString(), item.getTextualUploadDate())
        }
    }

    @Test
    fun sdkMetadataCreationDatePrecedesTheClaimUpdateWhenReleaseIsInvalid() {
        for (release in listOf(null, "invalid", "9223372036854775808", "0", "-1")) {
            val claim = JsonObject().apply {
                put("value", JsonObject().apply { put("release_time", release) })
                put("meta", JsonObject().apply { put("creation_timestamp", "1700000000") })
                put("timestamp", 1_790_000_000L)
            }
            assertEquals(1_700_000_000L, odyseeUploadDate(claim)?.toEpochSecond())
        }
    }

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
