package org.schabi.newpipe.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.fragments.MainFragment
import org.schabi.newpipe.fragments.list.kiosk.DefaultKioskFragment
import org.schabi.newpipe.settings.tabs.Tab
import org.schabi.newpipe.settings.tabs.TabsManager
import org.schabi.newpipe.util.KioskTranslator
import org.schabi.newpipe.util.ServiceHelper

class OdyseeStartupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test(timeout = 60_000)
    fun persistedOdyseeSelectionCanLaunchRefreshRotateAndRelaunch() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val serviceKey = context.getString(R.string.current_service_key)
        val tabsKey = context.getString(R.string.saved_tabs_key)
        val savedService = prefs.getString(serviceKey, null)
        val savedTabs = prefs.getString(tabsKey, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        try {
            ServiceHelper.setSelectedServiceId(context, ServiceList.Odysee.serviceId)
            TabsManager.getManager(context).saveTabs(listOf(Tab.DefaultKioskTab()))
            assertEquals(context.getString(R.string.search), Tab.DefaultKioskTab().getTabName(context))
            assertEquals(R.drawable.ic_search, Tab.DefaultKioskTab().getTabIconRes(context))
            repeat(2) {
                val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                    try {
                        assertSearchPrompt(scenario)
                        scenario.recreate()
                        assertSearchPrompt(scenario)
                    } finally {
                        onActivity(scenario) { it.finish() }
                    }
                }
            }
            assertEquals(ServiceList.Odysee.serviceId, ServiceHelper.getSelectedServiceId(context))
            assertEquals(R.drawable.ic_whatshot, KioskTranslator.getKioskIcon("Trending"))
        } finally {
            prefs.edit().putString(serviceKey, savedService).putString(tabsKey, savedTabs).commit()
        }
    }

    private fun assertSearchPrompt(scenario: ActivityScenario<MainActivity>) {
        onActivity(scenario) { activity ->
            activity.supportFragmentManager.executePendingTransactions()
            val main = activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) as MainFragment
            main.childFragmentManager.executePendingTransactions()
            val kiosk = main.childFragmentManager.fragments.filterIsInstance<DefaultKioskFragment>().single()
            kiosk.reloadContent()
            assertEquals(
                context.getString(R.string.main_bg_subtitle),
                kiosk.requireView().findViewById<TextView>(R.id.empty_state_message).text.toString()
            )
            assertTrue(kiosk.isResumed)
        }
    }

    private fun onActivity(scenario: ActivityScenario<MainActivity>, action: (MainActivity) -> Unit) {
        val completed = CountDownLatch(1)
        var failure: Throwable? = null
        Handler(Looper.getMainLooper()).post {
            try {
                scenario.onActivity { action(it) }
            } catch (error: Throwable) {
                failure = error
            } finally {
                completed.countDown()
            }
        }
        assertTrue("Activity callback did not complete", completed.await(10, TimeUnit.SECONDS))
        failure?.let { throw it }
    }
}
