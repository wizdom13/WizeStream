package org.schabi.newpipe.settings.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryArchiveTest {
    @Test
    fun jsonRoundTripPreservesUnicodeTimestampsAndRepeatCounts() {
        val archive = HistoryArchive(
            watches = listOf(ArchivedWatch(0, "https://example.com/watch", "درس, \"one\"\nTwo", "VIDEO_STREAM", 120, "Teacher", 123456789, 3)),
            searches = listOf(ArchivedSearch(1, "space & time", 123456790))
        )
        assertEquals(archive, HistoryArchiveCodec.decode(HistoryArchiveCodec.encode(archive).byteInputStream()))
    }

    @Test
    fun rejectsUnrelatedFilesFutureVersionsAndInvalidRecords() {
        listOf("{}", "{\"format\":\"wizestream-history\",\"version\":2}").forEach { text ->
            assertThrows(IllegalArgumentException::class.java) { HistoryArchiveCodec.decode(text.byteInputStream()) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            HistoryArchiveCodec.encode(HistoryArchive(searches = listOf(ArchivedSearch(0, "", -1))))
        }
    }

    @Test
    fun csvEscapesQuotesNewlinesAndSpreadsheetFormulas() {
        val csv = HistoryArchiveCodec.csv(HistoryArchive(searches = listOf(ArchivedSearch(0, "=SUM(1,2)\n\"quoted\"", 0))))
        assertTrue(csv.contains("\"'=SUM(1,2)\n\"\"quoted\"\"\""))
        assertTrue(csv.contains("1970-01-01T00:00:00Z"))
    }
}
