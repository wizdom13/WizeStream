package org.schabi.newpipe.profiles

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.schabi.newpipe.App
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.SavedSearchFeedEntity
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs
import org.schabi.newpipe.extractor.search.filter.FilterItem
import org.schabi.newpipe.settings.SettingsActivity
import org.schabi.newpipe.util.DeviceUtils
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.NavigationHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class KidsModeEntryPointsTest {
    private lateinit var context: Context
    private lateinit var appInstance: MockedStatic<App>
    private lateinit var deviceUtils: MockedStatic<DeviceUtils>

    @Before
    fun setup() {
        RxJavaPlugins.setComputationSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
        context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        context.getSharedPreferences(KidsMode.PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
        KidsMode.lock()
        val child = requireNotNull(ProfileManager.createProfile(context, "Child"))
        assertTrue(KidsMode.setPin(context, "1234"))
        KidsMode.grantParentAccess()
        assertTrue(KidsMode.setEnabled(context, child.id, true))
        KidsMode.lock()
        val app = mock(App::class.java)
        `when`(app.applicationContext).thenReturn(context)
        `when`(app.packageManager).thenReturn(context.packageManager)
        `when`(app.getString(R.string.kids_mode_search_disabled)).thenReturn("Search disabled")
        appInstance = mockStatic(App::class.java)
        // Java production callers use the @JvmStatic bridge, not Kotlin's companion getter.
        appInstance.`when`<App> {
            App::class.java.getMethod("getInstance").invoke(null)
        }.thenReturn(app)
        deviceUtils = mockStatic(DeviceUtils::class.java)
    }

    @After
    fun cleanup() {
        KidsMode.lock()
        try {
            if (::deviceUtils.isInitialized) deviceUtils.close()
        } finally {
            if (::appInstance.isInitialized) appInstance.close()
            RxJavaPlugins.reset()
            RxAndroidPlugins.reset()
        }
    }

    @Test
    fun blocksNewSearchesContinuationsSuggestionsAndServerChannelSearch() {
        ExtractorHelper.searchForFilters(0, "query", emptyList(), emptyList()).test()
            .assertError(IllegalStateException::class.java)
        ExtractorHelper.searchFor(0, "query", emptyList(), "").test()
            .assertError(IllegalStateException::class.java)
        ExtractorHelper.getMoreSearchItems(0, "query", emptyList(), emptyList(), Page("next"))
            .test().assertError(IllegalStateException::class.java)
        ExtractorHelper.suggestionsFor(0, "query").test()
            .assertError(IllegalStateException::class.java)
        val channelSearch = ServiceList.YouTube.channelTabLHFactory.fromQuery(
            "channel/UCAAAAAAAAAAAAAAAAAAAAAA",
            listOf(FilterItem(-1, ChannelTabs.SEARCH)),
            null
        )
        ExtractorHelper.getChannelTab(0, channelSearch, false).test()
            .assertError(IllegalStateException::class.java)
        ExtractorHelper.getMoreChannelTabItems(0, channelSearch, Page("next")).test()
            .assertError(IllegalStateException::class.java)
    }

    @Test
    fun blocksSearchNavigationBeforeCreatingFragmentTransaction() {
        val manager = mock(androidx.fragment.app.FragmentManager::class.java)
        NavigationHelper.openSearchFragment(manager, 0, "query")
        val feed = mock(SavedSearchFeedEntity::class.java)
        `when`(feed.serviceId).thenReturn(0)
        NavigationHelper.openSavedSearchFeed(manager, feed)
        NavigationHelper.openSearch(context, 0, "voice query")
        verifyNoInteractions(manager)
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
    }

    @Test
    fun directSettingsLaunchRequiresPinAndCancellingClosesSettings() {
        val intent = Intent(context, SettingsActivity::class.java)
            .putExtra(SettingsActivity.EXTRA_OPEN_PROFILES_SETTINGS, true)
        Robolectric.buildActivity(SettingsActivity::class.java, intent).use { controller ->
            val activity = controller.setup().visible().get()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            assertTrue(dialog.isShowing)
            assertFalse(activity.findViewById<View>(R.id.settings_fragment_holder).isShown)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(activity.isFinishing)
            assertTrue(KidsMode.requiresParentAccess(context))
        }
    }

    @Test
    fun correctPinOpensSettingsAndDoesNotLiftSearchRestriction() {
        Robolectric.buildActivity(SettingsActivity::class.java).use { controller ->
            val activity = controller.setup().visible().get()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            val input = firstPinInput(requireNotNull(dialog.window).decorView)
            requireNotNull(input).setText("1234")
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertFalse(dialog.isShowing)
            assertTrue(activity.findViewById<View>(R.id.settings_fragment_holder).isShown)
            assertFalse(KidsMode.requiresParentAccess(context))
            assertFalse(KidsMode.isSearchAllowed(context, 0))
        }
    }

    private fun firstPinInput(view: View): EditText? {
        if (view is EditText) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                firstPinInput(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    @Test
    fun leavingSettingsClearsParentSessionAndRequiresPinOnReturn() {
        KidsMode.grantParentAccess()
        Robolectric.buildActivity(SettingsActivity::class.java).use { controller ->
            controller.setup().visible()
            assertFalse(KidsMode.requiresParentAccess(context))
            controller.pause().stop()
            assertTrue(KidsMode.requiresParentAccess(context))
            controller.start().resume().visible()
            val dialog = ShadowDialog.getLatestDialog()
            assertNotNull(dialog)
            assertTrue(dialog.isShowing)
        }
    }
}
