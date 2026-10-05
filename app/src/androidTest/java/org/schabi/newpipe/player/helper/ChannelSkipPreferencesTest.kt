package org.schabi.newpipe.player.helper

import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.profiles.ProfileIcon
import org.schabi.newpipe.profiles.ProfileManager

class ChannelSkipPreferencesTest {
    @Test
    fun durationsPersistInBackupPreferencesAndDoNotLeakAcrossProfilesOrChannels() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val originalProfile = ProfileManager.getActiveProfileId(context)
        val first = requireNotNull(ProfileManager.createProfile(context, "Skip " + UUID.randomUUID(), "", ProfileIcon.PERSON.key))
        val info = StreamInfo(0, "fixture", "https://example.test/video", "Video").apply {
            uploaderUrl = "https://example.test/channel"
        }
        val key = requireNotNull(ChannelSkipPreferences.key(first.id, info.serviceId, info.uploaderUrl))
        var secondId: String? = null
        try {
            ChannelSkipPreferences.save(context, info, 15, 20)
            assertEquals(15, prefs.getInt(key + ".start", 0))
            assertEquals(20, prefs.getInt(key + ".end", 0))
            assertEquals(15, ChannelSkipPreferences.startSeconds(context, info))
            info.uploaderUrl = "https://example.test/other-channel"
            assertEquals(0, ChannelSkipPreferences.startSeconds(context, info))
            info.uploaderUrl = "https://example.test/channel"
            secondId = requireNotNull(ProfileManager.createProfile(context, "Skip " + UUID.randomUUID(), "", ProfileIcon.PERSON.key)).id
            assertEquals(0, ChannelSkipPreferences.startSeconds(context, info))
            ProfileManager.setActiveProfile(context, first.id)
            assertEquals(20, ChannelSkipPreferences.endSeconds(context, info))
            ChannelSkipPreferences.save(context, info, 0, 0)
            assertEquals(0, ChannelSkipPreferences.startSeconds(context, info))
            assertEquals(0, ChannelSkipPreferences.endSeconds(context, info))
        } finally {
            prefs.edit().remove(key + ".start").remove(key + ".end").commit()
            secondId?.let { ProfileManager.deleteProfile(context, it) }
            ProfileManager.deleteProfile(context, first.id)
            ProfileManager.setActiveProfile(context, originalProfile)
        }
    }

    @Test
    fun channelIdentityIsStableAndIsolatedByProfileAndService() {
        val url = "https://example.com/channel/name"
        val key = ChannelSkipPreferences.key("profile-a", 0, url)
        assertEquals(key, ChannelSkipPreferences.key("profile-a", 0, url))
        assertNotEquals(key, ChannelSkipPreferences.key("profile-b", 0, url))
        assertNotEquals(key, ChannelSkipPreferences.key("profile-a", 1, url))
        assertNotEquals(key, ChannelSkipPreferences.key("profile-a", 0, "$url-other"))
        assertNull(ChannelSkipPreferences.key("profile-a", 0, "  "))
    }
}
