/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player

import android.app.Application
import androidx.preference.PreferenceManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.playqueue.PlayQueueItem

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ChannelPlaybackMuteControllerTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val preferences get() = PreferenceManager.getDefaultSharedPreferences(context)

    @Before
    fun clearPreferences() {
        preferences.edit().clear().commit()
    }

    @Test
    fun channelChoiceSurvivesRecreationAndDoesNotMuteOtherChannelsOrServices() {
        val first = controller()
        first.select(0, "video-a", "channel-a")
        first.toggle()
        assertTrue(first.isMuted)

        val restarted = controller()
        restarted.select(0, "another-video", "channel-a")
        assertTrue(restarted.isMuted)
        restarted.select(0, "video-b", "channel-b")
        assertFalse(restarted.isMuted)
        restarted.select(1, "video-c", "channel-a")
        assertFalse(restarted.isMuted)
        restarted.select(0, "video-a", "channel-a")
        assertTrue(restarted.isMuted)
        restarted.toggle()
        assertFalse(restarted.isMuted)

        controller().also {
            it.select(0, "third-video", "channel-a")
            assertFalse(it.isMuted)
        }
    }

    @Test
    fun explicitChoiceBeforeLateMetadataIsRememberedWithoutBeingOverridden() {
        val value = controller()
        value.select(0, "video-a", null)
        value.toggle()
        value.onMetadata(info("video-a", "channel-a"))
        assertTrue(value.isMuted)
        assertTrue(value.hasChannelIdentity)
        repeat(3) { value.onMetadata(info("video-a", "channel-a")) }
        assertTrue(value.isMuted)
        controller().also {
            it.select(0, "video-b", "channel-a")
            assertTrue(it.isMuted)
        }
    }

    @Test
    fun lateMetadataUsesTheLatestManualChoiceAndNeverThePreviousVideo() {
        controller().also {
            it.select(0, "existing", "channel-a")
            it.toggle()
        }
        val value = controller()
        value.select(0, "video-a", null)
        value.toggle()
        value.toggle()
        value.onMetadata(info("video-a", "channel-a"))
        assertFalse(value.isMuted)

        value.select(0, "video-b", null)
        value.onMetadata(info("video-a", "channel-a"))
        assertFalse(value.hasChannelIdentity)
        assertFalse(value.isMuted)
        value.toggle()
        value.onMetadata(info("video-a", "channel-a"))
        assertTrue(value.isMuted)
        assertFalse(value.hasChannelIdentity)
        controller().also {
            it.select(0, "video-c", "channel-a")
            assertFalse(it.isMuted)
        }
    }

    @Test
    fun missingChannelAndLocalMediaChoicesStayWithinTheirCurrentVideo() {
        val value = controller()
        value.select(0, "without-uploader", " ")
        value.toggle()
        assertTrue(value.isMuted)
        assertFalse(value.hasChannelIdentity)
        value.select(0, "unrelated", null)
        assertFalse(value.isMuted)
        val local = mock(PlayQueueItem::class.java)
        `when`(local.serviceId).thenReturn(0)
        `when`(local.url).thenReturn("file:///video.mp4")
        `when`(local.uploaderUrl).thenReturn("not-an-online-channel")
        `when`(local.isLocalMedia).thenReturn(true)
        value.begin(local)
        value.toggle()
        assertFalse(value.hasChannelIdentity)
        assertTrue(preferences.all.isEmpty())
    }

    @Test
    fun channelChoiceIsExplicitAndIndependentOfAutomaticSpeedAndCaptionProfiles() {
        preferences.edit().putBoolean(context.getString(R.string.per_channel_playback_profiles_key), false).commit()
        val value = controller()
        value.select(0, "video-a", "channel-a")
        value.toggle()
        controller().also {
            it.select(0, "video-b", "channel-a")
            assertTrue(it.isMuted)
        }
    }

    @Test
    fun malformedImportedFlagCannotCrashPlayback() {
        val value = controller()
        value.select(0, "video-a", "channel-a")
        value.toggle()
        val key = preferences.all.keys.single()
        preferences.edit().putString(key, "not a Boolean").commit()
        controller().also {
            it.select(0, "video-b", "channel-a")
            assertFalse(it.isMuted)
        }
    }

    private fun controller() = ChannelPlaybackMuteController(context) {}

    private fun info(url: String, channel: String) = StreamInfo(0, url, url, url).apply { uploaderUrl = channel }
}
