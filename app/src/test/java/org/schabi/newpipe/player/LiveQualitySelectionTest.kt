package org.schabi.newpipe.player

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.preference.PreferenceManager
import java.util.Optional
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkInfo
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.util.ListHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LiveQualitySelectionTest {
    @Test
    fun automaticPlaybackIsCappedOnMeteredNetworksAndResetRestoresConstraints() {
        val context = RuntimeEnvironment.getApplication()
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        shadowOf(connectivity).setActiveNetworkInfo(
            ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED, ConnectivityManager.TYPE_MOBILE, 0, true, true)
        )
        assertTrue(ListHelper.isMeteredNetwork(context))
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(context.getString(R.string.default_resolution_key), context.getString(R.string.auto_resolution_key))
            .putString(context.getString(R.string.limit_mobile_data_usage_key), "480p").commit()
        val player = mock(Player::class.java)
        val engine = mock(ExoPlayer::class.java)
        val info = mock(StreamInfo::class.java)
        val selector = DefaultTrackSelector(context)
        val original = selector.parameters
        `when`(player.context).thenReturn(context)
        `when`(player.videoPlayerSelected()).thenReturn(true)
        `when`(player.exoPlayer).thenReturn(engine)
        `when`(player.trackSelector).thenReturn(selector)
        `when`(player.currentStreamInfo).thenReturn(Optional.of(info))
        `when`(info.streamType).thenReturn(StreamType.LIVE_STREAM)
        `when`(info.url).thenReturn("https://example.com/live")
        `when`(engine.currentTracks).thenReturn(Tracks.EMPTY)
        val controller = LiveQualityController(player)
        try {
            controller.onItemLoaded()
            assertTrue(controller.isAutoSelected)
            assertEquals(480, selector.parameters.maxVideoHeight)
            assertFalse(selector.parameters.exceedVideoConstraintsIfNecessary)
            controller.resetForNewItem()
            assertEquals(original.maxVideoHeight, selector.parameters.maxVideoHeight)
            assertEquals(original.exceedVideoConstraintsIfNecessary, selector.parameters.exceedVideoConstraintsIfNecessary)
        } finally {
            selector.release()
        }
    }

    @Test
    fun archiveManifestSelectionSurvivesRefreshAndAutoClearsOverride() {
        val context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(context.getString(R.string.default_resolution_key), "720p").commit()
        val player = mock(Player::class.java)
        val engine = mock(ExoPlayer::class.java)
        val info = mock(StreamInfo::class.java)
        val selector = DefaultTrackSelector(context)
        `when`(player.context).thenReturn(context)
        `when`(player.videoPlayerSelected()).thenReturn(true)
        `when`(player.exoPlayer).thenReturn(engine)
        `when`(player.trackSelector).thenReturn(selector)
        `when`(player.currentStreamInfo).thenReturn(Optional.of(info))
        `when`(info.streamType).thenReturn(StreamType.POST_LIVE_STREAM)
        `when`(info.serviceId).thenReturn(0)
        `when`(info.hlsUrl).thenReturn("https://example.com/archive.m3u8")
        `when`(info.url).thenReturn("https://example.com/live")
        fun tracks(id: String): Tracks {
            val group = TrackGroup(
                id,
                *listOf(1080, 720, 480).map {
                    Format.Builder().setSampleMimeType("video/avc").setCodecs("avc1.64001f")
                        .setHeight(it).setWidth(it * 16 / 9).build()
                }.toTypedArray()
            )
            return Tracks(listOf(Tracks.Group(group, true, intArrayOf(C.FORMAT_UNSUPPORTED_SUBTYPE, C.FORMAT_HANDLED, C.FORMAT_HANDLED), booleanArrayOf(false, true, false))))
        }
        val initial = tracks("initial")
        `when`(engine.currentTracks).thenReturn(initial)
        val controller = LiveQualityController(player)
        try {
            controller.onItemLoaded()
            assertFalse(controller.isAutoSelected)
            assertEquals(listOf(720, 480), controller.options.map { it.height })
            assertEquals(listOf(1), selector.parameters.overrides.values.single().trackIndices)
            val refreshed = tracks("refresh")
            `when`(engine.currentTracks).thenReturn(refreshed)
            controller.onTracksChanged(refreshed)
            assertEquals(refreshed.groups[0].mediaTrackGroup, selector.parameters.overrides.keys.single())
            controller.select(0)
            controller.onTracksChanged(refreshed)
            assertTrue(controller.isAutoSelected)
            assertTrue(selector.parameters.overrides.isEmpty())
            controller.select(1)
            controller.resetForNewItem()
            assertTrue(selector.parameters.overrides.isEmpty())
        } finally {
            selector.release()
        }
    }
}
