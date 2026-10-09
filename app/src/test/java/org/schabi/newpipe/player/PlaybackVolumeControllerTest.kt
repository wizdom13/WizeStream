package org.schabi.newpipe.player

import android.app.Application
import android.content.Context
import android.media.AudioManager
import androidx.media3.exoplayer.ExoPlayer
import androidx.preference.PreferenceManager
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.player.helper.AudioReactor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlaybackVolumeControllerTest {
    private lateinit var context: Context
    private lateinit var controller: PlaybackVolumeController
    private var modeChanges = 0
    private var volumeChanges = 0

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        controller = newController()
        controller.attach()
    }

    @Test
    fun quietLevelSurvivesRecreationAndModeChanges() {
        assertFalse(controller.isEnabled)
        assertGain(1f)
        enable(true)
        controller.level = 0.03f
        assertGain(0.03f)
        controller.detach()
        controller = newController()
        controller.attach()
        assertGain(0.03f)
        enable(false)
        assertGain(1f)
        enable(true)
        assertGain(0.03f)
        assertEquals(3, modeChanges)
    }

    @Test
    fun muteFocusEqualizerAndSleepFadePreserveUserAttenuation() {
        enable(true)
        controller.level = 0.03f
        controller.setFocusMultiplier(0.2f)
        assertEquals(0.0024f, controller.effectiveVolume(false, 0.5f, 0.8f), 0.000001f)
        assertEquals(0f, controller.effectiveVolume(true, 0.5f, 0.8f), 0f)
        controller.setFocusMultiplier(1f)
        assertGain(0.03f)
    }

    @Test
    fun attenuationNeverBoostsOrProducesInvalidEngineVolume() {
        enable(true)
        controller.level = -1f
        assertGain(0f)
        controller.level = 2f
        assertGain(1f)
        controller.level = Float.NaN
        controller.setFocusMultiplier(Float.POSITIVE_INFINITY)
        assertEquals(1f, controller.effectiveVolume(false, 2f, Float.NaN), 0f)
    }

    @Test
    fun focusRecoveryRestoresQuietLevelWithoutChangingDeviceVolume() {
        enable(true)
        controller.level = 0.03f
        val audioManager = context.getSystemService(AudioManager::class.java)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 1, 0)
        val engine = mock(ExoPlayer::class.java)
        val reactor = AudioReactor(context, engine, controller::setFocusMultiplier)
        reactor.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        assertGain(0.006f)
        reactor.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertGain(0.03f)
        assertEquals(1, audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
        verify(engine, never()).setVolume(org.mockito.ArgumentMatchers.anyFloat())
        reactor.dispose()
    }

    @Test
    fun secondFocusLossCancelsRecoveryAndDisposeCancelsCallbacks() {
        enable(true)
        controller.level = 0.03f
        val reactor = AudioReactor(context, mock(ExoPlayer::class.java), controller::setFocusMultiplier)
        reactor.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        reactor.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertGain(0.006f)
        reactor.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        reactor.dispose()
        val changesAtDispose = volumeChanges
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(changesAtDispose, volumeChanges)
    }

    private fun newController() = PlaybackVolumeController(context, { volumeChanges++ }, { modeChanges++ })

    private fun enable(enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(context.getString(R.string.independent_player_volume_key), enabled).commit()
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    private fun assertGain(expected: Float) {
        assertEquals(expected, controller.effectiveVolume(false, 1f, 1f), 0.000001f)
    }
}
