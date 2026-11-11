package org.schabi.newpipe.player.ui

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.PlaybackPresentationMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DirectMediaPresentationTest {
    @Test
    fun liveTvShowsLiveControlsAndReturningToLocalVideoRestoresDuration() {
        val live = View(RuntimeEnvironment.getApplication())
        val endTime = View(live.context)
        DirectMediaPresentation.updateLiveControls(live, endTime, StreamType.LIVE_STREAM)
        assertEquals(View.VISIBLE, live.visibility)
        assertEquals(View.GONE, endTime.visibility)
        DirectMediaPresentation.updateLiveControls(live, endTime, StreamType.VIDEO_STREAM)
        assertEquals(View.GONE, live.visibility)
        assertEquals(View.VISIBLE, endTime.visibility)
    }

    @Test
    fun switchingFromVideoToRadioHidesSurfaceAndReturningToVideoRestoresIt() {
        val surface = View(RuntimeEnvironment.getApplication())
        val artwork = View(surface.context)
        DirectMediaPresentation.update(surface, artwork, StreamType.AUDIO_LIVE_STREAM, PlaybackPresentationMode.AUDIO_BACKGROUND)
        assertEquals(View.GONE, surface.visibility)
        assertEquals(View.VISIBLE, artwork.visibility)
        DirectMediaPresentation.update(surface, artwork, StreamType.AUDIO_LIVE_STREAM, PlaybackPresentationMode.LISTEN_VISUALIZER)
        assertEquals(View.GONE, surface.visibility)
        assertEquals(View.GONE, artwork.visibility)
        DirectMediaPresentation.update(surface, artwork, StreamType.VIDEO_STREAM, PlaybackPresentationMode.VIDEO)
        assertEquals(View.VISIBLE, surface.visibility)
        assertEquals(View.GONE, artwork.visibility)
    }
}
