package org.schabi.newpipe.player.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.ui.FullscreenOrientationPolicy.VideoContentOrientation

class StartFullscreenRequestTest {
    private val item = mock(PlayQueueItem::class.java).also {
        `when`(it.url).thenReturn("https://example.com/video")
        `when`(it.serviceId).thenReturn(0)
    }

    private fun playerUi(): MainPlayerUi {
        val player = mock(Player::class.java)
        `when`(player.currentItem).thenReturn(item)
        return mock(MainPlayerUi::class.java).also { `when`(it.getPlayer()).thenReturn(player) }
    }

    @Test
    fun unknownDimensionsEnterFullscreenWithoutRequestingLandscape() {
        val ui = playerUi()
        `when`(ui.videoContentOrientation).thenReturn(VideoContentOrientation.UNKNOWN)
        var fullscreen = false
        doAnswer {
            fullscreen = true
            null
        }.`when`(ui).setFullscreen(true)
        `when`(ui.isFullscreen).thenAnswer { fullscreen }
        val align = mock(Runnable::class.java)
        val request = StartFullscreenRequest()
        request.request(item)
        request.apply(ui, align)
        assertTrue(fullscreen)
        verify(align, never()).run()
        assertTrue(
            FullscreenOrientationPolicy.shouldAlignFullscreenToKnownContent(
                fullscreen,
                VideoContentOrientation.UNKNOWN,
                VideoContentOrientation.PORTRAIT
            )
        )
        // A later attachment must not re-enter fullscreen after the user exits it.
        fullscreen = false
        request.apply(ui, align)
        assertFalse(fullscreen)
    }

    @Test
    fun knownContentAlignsOnlyAfterFullscreenSucceeds() {
        for (orientation in listOf(VideoContentOrientation.PORTRAIT, VideoContentOrientation.LANDSCAPE, VideoContentOrientation.SQUARE)) {
            val ui = playerUi()
            `when`(ui.videoContentOrientation).thenReturn(orientation)
            val align = mock(Runnable::class.java)
            val request = StartFullscreenRequest()
            request.request(item)
            // setFullscreen cannot succeed until the playback engine and listener are attached.
            request.apply(ui, align)
            verify(align, never()).run()
            `when`(ui.isFullscreen).thenReturn(true)
            request.apply(ui, align)
            request.apply(ui, align)
            verify(align).run()
        }
    }

    @Test
    fun previousVideoCannotConsumeTheNewVideosRequest() {
        val ui = playerUi()
        val previous = mock(PlayQueueItem::class.java)
        `when`(previous.url).thenReturn("https://example.com/previous")
        `when`(ui.getPlayer().currentItem).thenReturn(previous)
        val align = mock(Runnable::class.java)
        val request = StartFullscreenRequest()
        request.request(item)
        request.apply(ui, align)
        verify(ui, never()).setFullscreen(true)
        `when`(ui.getPlayer().currentItem).thenReturn(item)
        `when`(ui.isFullscreen).thenReturn(true)
        `when`(ui.videoContentOrientation).thenReturn(VideoContentOrientation.PORTRAIT)
        request.apply(ui, align)
        verify(ui).setFullscreen(true)
        verify(align).run()
    }

    @Test
    fun normalPlaybackWithoutAnExplicitRequestDoesNotEnterFullscreen() {
        val ui = playerUi()
        val align = mock(Runnable::class.java)
        StartFullscreenRequest().apply(ui, align)
        verify(ui, never()).setFullscreen(true)
        verify(align, never()).run()
    }
}
