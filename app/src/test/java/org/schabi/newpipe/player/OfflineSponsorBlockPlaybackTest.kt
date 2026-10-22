package org.schabi.newpipe.player

import androidx.media3.exoplayer.ExoPlayer
import androidx.preference.PreferenceManager
import java.util.Optional
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockAction
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockCategory
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment
import org.schabi.newpipe.player.ui.PlayerUiList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class OfflineSponsorBlockPlaybackTest {
    @Test
    fun localPlaybackUsesSavedSegmentsAndHonorsTheMasterSwitch() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val player = mock(Player::class.java)
        val exo = mock(ExoPlayer::class.java)
        `when`(player.context).thenReturn(context)
        `when`(player.prefs).thenReturn(prefs)
        `when`(player.exoPlayer).thenReturn(exo)
        `when`(player.isPlaying).thenReturn(true)
        `when`(player.currentStreamInfo).thenReturn(Optional.empty())
        `when`(player.UIs()).thenReturn(PlayerUiList())
        `when`(exo.currentPosition).thenReturn(15_000)
        `when`(exo.duration).thenReturn(60_000)
        `when`(exo.playbackState).thenReturn(androidx.media3.common.Player.STATE_READY)
        prefs.edit().putBoolean(context.getString(R.string.sponsor_block_enable_key), true)
            .putBoolean(context.getString(R.string.sponsor_block_notifications_key), false).commit()
        val controller = SponsorBlockPlaybackController(player)
        controller.updateSegments(listOf(SponsorBlockSegment("saved", 10_000.0, 20_000.0, SponsorBlockCategory.SPONSOR, SponsorBlockAction.SKIP, 0)))
        prefs.edit().putBoolean(context.getString(R.string.sponsor_block_enable_key), false).commit()
        controller.onProgress()
        verify(exo, never()).seekTo(20_000)
        prefs.edit().putBoolean(context.getString(R.string.sponsor_block_enable_key), true).commit()
        controller.onProgress()
        verify(exo).seekTo(20_000)
    }
}
