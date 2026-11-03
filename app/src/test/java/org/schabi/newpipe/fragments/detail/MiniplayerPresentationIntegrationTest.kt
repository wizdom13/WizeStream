package org.schabi.newpipe.fragments.detail

import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.RelativeLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding
import org.schabi.newpipe.databinding.PlayerBinding
import org.schabi.newpipe.player.PlaybackPresentationMode
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.event.PlayerServiceEventListener
import org.schabi.newpipe.player.helper.PlayerHolder
import org.schabi.newpipe.player.ui.MainPlayerUi
import org.schabi.newpipe.player.ui.MinimizedVideoSurface
import org.schabi.newpipe.player.ui.PlayerUiList
import org.schabi.newpipe.views.ExpandableSurfaceView

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MiniplayerPresentationIntegrationTest {
    private class Fixture(attached: Boolean = true) {
        val context = RuntimeEnvironment.getApplication()
        val original = FrameLayout(context)
        val host = FrameLayout(context)
        val thumbnail = ImageButton(context)
        val surface = ExpandableSurfaceView(context, null)
        val fragment = mock(VideoDetailFragment::class.java, CALLS_REAL_METHODS)
        val player = mock(Player::class.java)
        val ui = mock(MainPlayerUi::class.java)
        val holder = PlayerHolder::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        val relay: PlayerServiceEventListener
        val binding = mock(FragmentVideoDetailBinding::class.java)

        init {
            if (attached) original.addView(surface)
            field(binding, "overlayVideo", host)
            field(binding, "overlayThumbnail", thumbnail)
            field(binding, "overlayLayout", RelativeLayout(context))
            val playerBinding = mock(PlayerBinding::class.java)
            field(playerBinding, "surfaceView", surface)
            `when`(ui.getBinding()).thenReturn(playerBinding)
            `when`(player.UIs()).thenReturn(PlayerUiList(ui))
            `when`(player.videoPlayerSelected()).thenReturn(true)
            field(fragment, "binding", binding)
            field(fragment, "player", player)
            field(fragment, "bottomSheetState", BottomSheetBehavior.STATE_COLLAPSED)
            field(fragment, "minimizedVideoSurface", MinimizedVideoSurface())
            holder.setListener(fragment)
            relay = PlayerHolder::class.java.getDeclaredField("internalListener").apply {
                isAccessible = true
            }.get(holder) as PlayerServiceEventListener
        }

        fun changeMode(mode: PlaybackPresentationMode) {
            `when`(player.playbackPresentationMode).thenReturn(mode)
            `when`(player.isAudioOnly).thenReturn(!mode.rendersVideo())
            relay.onPlaybackPresentationModeChanged(mode)
        }
    }

    @Test
    fun forwardedModeChangesMoveTheSurfaceAndRebindOnlyAfterActualMoves() {
        val f = Fixture()
        f.changeMode(PlaybackPresentationMode.VIDEO)
        assertSame(f.host, f.surface.parent)
        assertEquals(View.VISIBLE, f.host.visibility)
        assertEquals(0f, f.thumbnail.alpha, 0f)
        f.changeMode(PlaybackPresentationMode.VIDEO)
        verify(f.ui, times(1)).restoreVideoSurfaceAfterLayoutTransition()

        f.changeMode(PlaybackPresentationMode.LISTEN_VISUALIZER)
        assertSame(f.original, f.surface.parent)
        assertEquals(View.GONE, f.host.visibility)
        assertEquals(1f, f.thumbnail.alpha, 0f)
        f.changeMode(PlaybackPresentationMode.AUDIO_BACKGROUND)
        verify(f.ui, times(2)).restoreVideoSurfaceAfterLayoutTransition()

        f.changeMode(PlaybackPresentationMode.VIDEO)
        assertSame(f.host, f.surface.parent)
        verify(f.ui, times(3)).restoreVideoSurfaceAfterLayoutTransition()
        field(f.fragment, "bottomSheetState", BottomSheetBehavior.STATE_EXPANDED)
        f.changeMode(PlaybackPresentationMode.VIDEO)
        f.changeMode(PlaybackPresentationMode.VIDEO)
        assertSame(f.original, f.surface.parent)
        verify(f.ui, times(4)).restoreVideoSurfaceAfterLayoutTransition()
    }

    @Test
    fun unattachedSurfaceKeepsThumbnailUntilARefreshCanHostIt() {
        val f = Fixture(attached = false)
        f.changeMode(PlaybackPresentationMode.VIDEO)
        assertEquals(View.GONE, f.host.visibility)
        assertEquals(1f, f.thumbnail.alpha, 0f)
        verify(f.ui, never()).restoreVideoSurfaceAfterLayoutTransition()
        f.original.addView(f.surface)
        f.changeMode(PlaybackPresentationMode.VIDEO)
        assertSame(f.host, f.surface.parent)
        verify(f.ui).restoreVideoSurfaceAfterLayoutTransition()
    }

    @Test
    fun detachedListenerAndDestroyedFragmentViewIgnoreLateModeUpdates() {
        val f = Fixture()
        field(f.fragment, "binding", null)
        f.changeMode(PlaybackPresentationMode.VIDEO)
        verify(f.ui, never()).restoreVideoSurfaceAfterLayoutTransition()
        field(f.fragment, "binding", f.binding)
        f.holder.setListener(null)
        clearInvocations(f.fragment)
        f.changeMode(PlaybackPresentationMode.VIDEO)
        verify(f.fragment, never()).onPlaybackPresentationModeChanged(PlaybackPresentationMode.VIDEO)
        assertSame(f.original, f.surface.parent)
    }

    companion object {
        private fun field(target: Any, name: String, value: Any?) {
            var type: Class<*>? = target.javaClass
            while (type != null) {
                try {
                    type.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
                    return
                } catch (_: NoSuchFieldException) {
                    type = type.superclass
                }
            }
            error("Missing field: $name")
        }
    }
}
