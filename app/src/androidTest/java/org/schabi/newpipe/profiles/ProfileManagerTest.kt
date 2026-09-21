package org.schabi.newpipe.profiles

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileManagerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun synchronizedProfileKeepsStableIdentityAndLocalDeletionTombstonesIt() {
        val profileId = "33333333-3333-3333-3333-333333333333"
        try {
            assertEquals(
                ProfileSyncMergeResult.ADDED,
                ProfileManager.upsertSyncedProfile(
                    context,
                    profileId,
                    "Study",
                    "Synced profile",
                    ProfileIcon.STUDY.key,
                    10L,
                    20L
                )
            )
            assertEquals("Study", ProfileManager.getProfile(context, profileId)?.name)
            assertEquals(20L, ProfileManager.getProfileSyncUpdatedAt(context, profileId))

            assertTrue(ProfileManager.deleteProfile(context, profileId))
            assertTrue(ProfileManager.isProfileSyncTombstoned(context, profileId))
            assertEquals(
                ProfileSyncMergeResult.TOMBSTONED,
                ProfileManager.upsertSyncedProfile(
                    context,
                    profileId,
                    "Study",
                    "Remote copy",
                    ProfileIcon.STUDY.key,
                    10L,
                    30L
                )
            )
            assertEquals(null, ProfileManager.getProfile(context, profileId))
        } finally {
            ProfileManager.setActiveProfile(context, ProfileManager.DEFAULT_PROFILE_ID)
        }
    }

    @Test
    fun defaultProfileExistsAndCustomProfileLifecycleIsIsolated() {
        val initialProfiles = ProfileManager.getProfiles(context)
        assertTrue(initialProfiles.any { it.id == ProfileManager.DEFAULT_PROFILE_ID })

        val uniqueName = "Test " + UUID.randomUUID().toString().take(8)
        val created = ProfileManager.createProfile(
            context,
            uniqueName,
            "instrumentation profile",
            ProfileIcon.STUDY.key
        )
        assertNotNull(created)

        val profile = requireNotNull(created)
        try {
            assertEquals(profile.id, ProfileManager.getActiveProfileId(context))
            assertEquals(ProfileIcon.STUDY.key, profile.iconKey)

            assertTrue(
                ProfileManager.updateProfile(
                    context,
                    profile.id,
                    uniqueName + " updated",
                    "updated description",
                    ProfileIcon.WORK.key
                )
            )
            val updated = ProfileManager.getProfile(context, profile.id)
            assertEquals(ProfileIcon.WORK.key, updated?.iconKey)
            assertEquals("updated description", updated?.description)

            assertFalse(ProfileManager.deleteProfile(context, ProfileManager.DEFAULT_PROFILE_ID))
            assertTrue(ProfileManager.deleteProfile(context, profile.id))
            assertEquals(
                ProfileManager.DEFAULT_PROFILE_ID,
                ProfileManager.getActiveProfileId(context)
            )
        } finally {
            ProfileManager.deleteProfile(context, profile.id)
            ProfileManager.setActiveProfile(context, ProfileManager.DEFAULT_PROFILE_ID)
        }
    }
}
