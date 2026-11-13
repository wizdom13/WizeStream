package org.schabi.newpipe.settings.export

import com.grack.nanojson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeTubeBackupTest {
    @Test
    fun modernSubscriptionProfilesAndLegacyChannelsBecomeChannelsAndGroups() {
        val modern = """{"_id":"allChannels","name":"Main","subscriptions":[{"id":"$CHANNEL","name":"Creator"}]}""" + "\n" +
            """{"_id":"science","name":"Science","subscriptions":[{"id":"$CHANNEL","name":"Creator"}]}"""
        val data = FreeTubeBackup.parse(modern)
        assertEquals(1, data.subscriptions.size)
        assertEquals(listOf("FreeTube · Main", "FreeTube · Science"), data.groups.map { it.name })
        assertEquals(CHANNEL, data.groups.last().channels.single())
        val legacy = FreeTubeBackup.parse("""{"channelId":"$CHANNEL","channelName":"Legacy"}""")
        assertEquals("Legacy", legacy.subscriptions.single().name)
    }

    @Test
    fun oldArraysAndNewJsonLinesKeepVideoOrderAndRepeatedOccurrences() {
        val playlist = """{"playlistName":"Study","videos":[{"videoId":"$VIDEO","title":"First","lengthSeconds":"120"},{"videoId":"lmnopqrstuv","title":"Second"},{"videoId":"$VIDEO","title":"First"}]}"""
        val expected = listOf(VIDEO, "lmnopqrstuv", VIDEO)
        assertEquals(expected, FreeTubeBackup.parse("[$playlist]").playlists.single().videos.map { it.id })
        assertEquals(expected, FreeTubeBackup.parse(playlist + "\n").playlists.single().videos.map { it.id })
        assertEquals(120L, FreeTubeBackup.parse(playlist).playlists.single().videos.first().duration)
    }

    @Test
    fun compactedDatabaseRecordsDoNotResurrectDeletedOrSupersededPlaylists() {
        val json = """{"_id":"a","playlistName":"Old","videos":[]}
            |{"_id":"a","playlistName":"New","videos":[]}
            |{"_id":"b","playlistName":"Deleted","videos":[]}
            |{"_id":"b","${'$'}${'$'}deleted":true}
            |{"${'$'}${'$'}indexCreated":{"fieldName":"playlistName"}}
        """.trimMargin()
        assertEquals(listOf("New"), FreeTubeBackup.parse(json).playlists.map { it.name })
    }

    @Test
    fun exportedSubscriptionsMatchFreeTubesProfileFormatAndRoundTrip() {
        val channels = listOf(LibreTubeChannel(CHANNEL, "Creator"))
        val output = FreeTubeBackup.exportSubscriptions("Personal", channels)
        assertTrue(output.endsWith("\n"))
        val row = JsonParser.`object`().from(output.trim())
        assertEquals("allChannels", row.getString("_id"))
        assertTrue(row.containsKey("bgColor") && row.containsKey("textColor"))
        assertEquals(channels, FreeTubeBackup.parse(output).subscriptions)
    }

    @Test
    fun exportedPlaylistsContainAllRequiredFreeTubeVideoFieldsAndRoundTrip() {
        val playlists = listOf(LibreTubePlaylist("Watch", listOf(LibreTubeVideo(VIDEO, "Title", "Creator", 120))))
        val output = FreeTubeBackup.exportPlaylists(playlists, 1234)
        val row = JsonParser.`object`().from(output.trim())
        assertEquals("Watch", row.getString("playlistName"))
        val video = row.getArray("videos").getObject(0)
        assertEquals(120L, video.getLong("lengthSeconds"))
        assertEquals(1234L, video.getLong("timeAdded"))
        assertTrue(video.getString("playlistItemId").isNotEmpty())
        assertEquals(playlists, FreeTubeBackup.parse(output).playlists)
    }

    @Test
    fun malformedAndOversizedInputsAreRejectedAndForeignUrlsAreSkipped() {
        listOf("{}", "broken", "{\"preferences\":[]}", "[".repeat(33) + "0" + "]".repeat(33)).forEach { value ->
            assertThrows(Exception::class.java) { FreeTubeBackup.parse(value) }
        }
        assertThrows(IllegalArgumentException::class.java) { FreeTubeBackup.parse(" ".repeat(FreeTubeBackup.MAX_BYTES + 1).byteInputStream()) }
        val data = FreeTubeBackup.parse("""{"playlistName":"Safe","videos":[{"videoId":"https://evil.test/watch?v=$VIDEO"},{"videoId":"https://youtu.be/$VIDEO"}]}""")
        assertEquals(1, data.skipped)
        assertEquals(VIDEO, data.playlists.single().videos.single().id)
    }

    companion object {
        private const val CHANNEL = "UCabcdefghijklmnopqrstuv"
        private const val VIDEO = "abcdefghijk"
    }
}
