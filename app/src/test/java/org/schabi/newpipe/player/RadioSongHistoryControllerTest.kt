package org.schabi.newpipe.player

import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.icy.IcyInfo
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.RETURNS_DEFAULTS
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.stubbing.Answer
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.local.radio.RadioSongHistoryStore
import org.schabi.newpipe.player.playqueue.PlayQueueItem

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RadioSongHistoryControllerTest {
    private val station = PlayQueueItem.radioStation("Station", "https://example.org/live", "Details", false, null)
    private val store = mock(
        RadioSongHistoryStore::class.java,
        Answer { invocation ->
            when (invocation.method.name) {
                "enabled" -> true
                "record" -> Completable.complete()
                else -> RETURNS_DEFAULTS.answer(invocation)
            }
        }
    )
    private var profile = "first"
    private val controller = RadioSongHistoryController(RuntimeEnvironment.getApplication(), CompositeDisposable(), store) { profile }

    @Test
    fun repeatsAndPauseResumeDoNotCreateExtraListensButAnotherSongDoes() {
        controller.onMetadata(station, song("Artist - First"), true)
        controller.onMetadata(station, song("Artist - First"), true)
        controller.onPlayback(station, false)
        controller.onPlayback(station, true)
        verify(store).record(station, "Artist - First", "first")
        controller.onMetadata(station, song("Second"), true)
        controller.onMetadata(station, song("Artist - First"), true)
        verify(store, times(2)).record(station, "Artist - First", "first")
    }

    @Test
    fun bufferingKeepsLatestSongAndStationSwitchDiscardsPendingTitle() {
        controller.onMetadata(station, song("Buffered"), false)
        controller.onMetadata(station, song("Latest"), false)
        controller.onPlayback(station, true)
        verify(store).record(station, "Latest", "first")
        controller.onMetadata(station, song("Discarded"), false)
        val other = PlayQueueItem.radioStation("Other", "https://other.org/live", null, true, null)
        controller.onPlayback(other, true)
        controller.onMetadata(other, song("Other song"), true)
        verify(store).record(other, "Other song", "first")
        verify(store, times(0)).record(station, "Discarded", "first")
    }

    @Test
    fun disabledHistoryCannotBeReplayedWhenEnabledAndProfilesStaySeparate() {
        `when`(store.enabled()).thenReturn(false)
        controller.onMetadata(station, song("Private"), true)
        `when`(store.enabled()).thenReturn(true)
        controller.onPlayback(station, true)
        controller.onMetadata(station, song("Private"), true)
        verify(store, times(0)).record(station, "Private", "first")
        profile = "second"
        controller.onMetadata(station, song("New profile"), true)
        verify(store).record(station, "New profile", "second")
    }

    @Test
    fun nonRadioPlaybackDoesNotTouchHistory() {
        controller.onMetadata(null, song("Not radio"), true)
        verifyNoInteractions(store)
    }

    @Test
    fun metadataParserKeepsUnicodeAndCombinesHlsArtistWithTrackTitle() {
        val metadata = Metadata(
            androidx.media3.extractor.metadata.id3.TextInformationFrame("TPE1", null, listOf("Fairuz")),
            androidx.media3.extractor.metadata.id3.TextInformationFrame("TIT2", null, listOf("  فيروز\nSong  "))
        )
        assertEquals("Fairuz - فيروز Song", org.schabi.newpipe.local.radio.RadioTrackMetadata.title(metadata))
        assertEquals(null, org.schabi.newpipe.local.radio.RadioTrackMetadata.title(song("   ")))
        org.junit.Assert.assertFalse(org.schabi.newpipe.local.radio.RadioTrackMetadata.containsTitle(Metadata()))
    }

    @Test
    fun historyKeepsSeparateSongsAndReconstructsLiveStationWithLogo() {
        val withLogo = PlayQueueItem.radioStation("Radio", "https://example.org/live?q=1", "Country", true, "https://example.org/logo.png")
        val first = requireNotNull(RadioSongHistoryStore.entity(withLogo, "First"))
        val second = requireNotNull(RadioSongHistoryStore.entity(withLogo, "Second"))
        org.junit.Assert.assertNotEquals(first.url, second.url)
        assertEquals("Radio", first.uploader)
        assertEquals("First", first.title)
        val replay = first.toPlayQueueItem()
        org.junit.Assert.assertTrue(replay.isRadio)
        assertEquals(withLogo.url, replay.url)
        assertEquals(withLogo.title, replay.title)
        assertEquals(withLogo.localThumbnailUrl, replay.localThumbnailUrl)
        assertEquals(withLogo.mimeType, replay.mimeType)
    }

    private fun song(title: String): Metadata = Metadata(IcyInfo(byteArrayOf(), title, null))
}
