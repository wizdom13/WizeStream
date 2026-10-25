package org.schabi.newpipe.profiles

import android.content.Context
import androidx.preference.PreferenceManager
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.extractor.ServiceList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28, 35])
class KidsModeTest {
    private lateinit var context: Context
    private lateinit var childId: String
    private val parentId = ProfileManager.DEFAULT_PROFILE_ID

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        context.getSharedPreferences(KidsMode.PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
        KidsMode.lock()
        childId = requireNotNull(ProfileManager.createProfile(context, "Child")).id
    }

    @After
    fun cleanup() {
        KidsMode.lock()
    }

    @Test
    fun restrictionsFollowProfilesAndCannotBeBypassedByProfileMutations() {
        enableKidsMode()
        assertFalse(KidsMode.isSearchAllowed(context, ServiceList.YouTube.serviceId))
        assertFalse(KidsMode.isSubscriptionAllowed(context, ServiceList.YouTube.serviceId))
        assertTrue(KidsMode.isSearchAllowed(context, ServiceList.SoundCloud.serviceId))
        assertFalse(ProfileManager.setActiveProfile(context, parentId))
        assertFalse(ProfileManager.setDefaultProfile(context, childId))
        assertFalse(ProfileManager.canDeleteProfile(context, childId))
        assertFalse(ProfileManager.deleteProfile(context, childId))
        assertNull(ProfileManager.createProfile(context, "Bypass"))
        assertFalse(KidsMode.setEnabled(context, childId, false))
        assertTrue(KidsMode.verifyPin(context, "1234"))
        KidsMode.grantParentAccess()
        // Parent access permits configuration, but does not silently lift content controls.
        assertFalse(KidsMode.isSearchAllowed(context, ServiceList.YouTube.serviceId))
        assertTrue(ProfileManager.setActiveProfile(context, parentId))
        KidsMode.lock()
        assertTrue(KidsMode.isSearchAllowed(context, ServiceList.YouTube.serviceId))
        assertTrue(ProfileManager.setActiveProfile(context, childId))
        assertTrue(KidsMode.requiresParentAccess(context))
    }

    @Test
    fun requiresValidPinAndUsesSaltedHashInsteadOfPlaintext() {
        assertFalse(KidsMode.setPin(context, "123"))
        assertFalse(KidsMode.setPin(context, "abcd"))
        assertFalse(KidsMode.setPin(context, "123456789"))
        assertFalse(KidsMode.setEnabled(context, childId, true))
        assertTrue(KidsMode.setPin(context, "1234"))
        val prefs = context.getSharedPreferences(KidsMode.PREFERENCES, Context.MODE_PRIVATE)
        val original = prefs.all.toMap()
        assertFalse(original.values.contains("1234"))
        assertFalse(KidsMode.setPin(context, "5678"))
        assertTrue(KidsMode.verifyPin(context, "1234"))
        KidsMode.grantParentAccess()
        assertTrue(KidsMode.setPin(context, "1234"))
        assertNotEquals(original["pin_salt"], prefs.all["pin_salt"])
        assertNotEquals(original["pin_hash"], prefs.all["pin_hash"])
        assertTrue(KidsMode.setPin(context, "5678"))
        assertFalse(KidsMode.verifyPin(context, "1234"))
        assertTrue(KidsMode.verifyPin(context, "5678"))
    }

    @Test
    fun failedAttemptsRemainThrottledAfterLockingAndExpire() {
        enableKidsMode()
        val now = 10_000L
        repeat(5) { assertFalse(KidsMode.verifyPin(context, "9999", now)) }
        KidsMode.lock()
        assertTrue(KidsMode.retryAfterMillis(context, now) > 0)
        assertFalse(KidsMode.verifyPin(context, "1234", now))
        assertTrue(KidsMode.requiresParentAccess(context))
        assertTrue(KidsMode.verifyPin(context, "1234", now + 31_000))
    }

    @Test
    fun parentCanDisableOnlySelectedProfileAndRelock() {
        enableKidsMode()
        assertTrue(KidsMode.verifyPin(context, "1234"))
        KidsMode.grantParentAccess()
        assertTrue(KidsMode.setEnabled(context, parentId, true))
        assertTrue(KidsMode.setEnabled(context, childId, false))
        KidsMode.lock()
        assertFalse(KidsMode.isEnabled(context))
        assertTrue(KidsMode.isEnabled(context, parentId))
        assertTrue(ProfileManager.setActiveProfile(context, parentId))
        assertTrue(KidsMode.requiresParentAccess(context))
    }

    @Test
    fun approvedDeletionCanFinishAfterSettingsRelocks() {
        enableKidsMode()
        assertNull(ProfileManager.prepareProfileDeletion(context, childId))
        assertTrue(KidsMode.verifyPin(context, "1234"))
        KidsMode.grantParentAccess()
        val finishDeletion = requireNotNull(ProfileManager.prepareProfileDeletion(context, childId))
        KidsMode.lock()
        assertFalse(ProfileManager.deleteProfile(context, childId))
        assertTrue(finishDeletion())
        assertNull(ProfileManager.getProfile(context, childId))
        assertFalse(KidsMode.isEnabled(context, childId))
        assertFalse(finishDeletion())
    }

    private fun enableKidsMode() {
        assertTrue(KidsMode.setPin(context, "1234"))
        KidsMode.grantParentAccess()
        assertTrue(KidsMode.setEnabled(context, childId, true))
        KidsMode.lock()
    }
}
