package org.schabi.newpipe.local.tv

import androidx.media3.exoplayer.hls.HlsMediaSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.schabi.newpipe.player.mediaitem.LocalMediaItemTag
import org.schabi.newpipe.player.playqueue.LocalMediaPlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.recovery.QueueSnapshot

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LiveTvPlaybackSourceTest {
    @Test
    fun extensionlessHlsKeepsMimeTypeTagAndVideoPresentation() {
        val item = PlayQueueItem.liveTv("Channel", "https://tv.example/play?token=private", "News", "application/x-mpegURL", "https://tv.example/logo.png")
        val source = requireNotNull(LiveTvPlaybackSource.create(item))
        assertTrue(source is HlsMediaSource)
        assertTrue(item.isLiveTv)
        assertTrue(item.isDirectMedia)
        assertFalse(item.isRadio)
        assertFalse(item.isLocalMedia)
        assertEquals(org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM, item.streamType)
        assertEquals("application/x-mpegURL", source.mediaItem.localConfiguration?.mimeType)
        assertEquals(item, (source.mediaItem.localConfiguration?.tag as LocalMediaItemTag).item)
        assertEquals("Channel", source.mediaItem.mediaMetadata.title)
        assertFalse(org.schabi.newpipe.player.playqueue.DirectMediaQueuePolicy.canSaveToLibrary(LocalMediaPlayQueue(listOf(item), 0)))
        assertNull(QueueSnapshot.capture(LocalMediaPlayQueue(listOf(item), 0), "session", 0, 1))
    }

    @Test
    fun rejectsUnsupportedDirectUrlsBeforeCreatingAPlaybackSource() {
        assertNull(LiveTvPlaybackSource.create(PlayQueueItem.liveTv("Bad", "http://tv.example/play", "", null, null)))
        assertNull(LiveTvPlaybackSource.create(PlayQueueItem.radioStation("Radio", "https://radio.example/play", "", false, null)))
    }
}
