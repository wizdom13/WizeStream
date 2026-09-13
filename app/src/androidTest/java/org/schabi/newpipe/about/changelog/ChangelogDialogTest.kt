package org.schabi.newpipe.about.changelog

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity

class ChangelogDialogTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun resetSeenRelease() {
        context.getSharedPreferences("changelog", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun clearSeenRelease() {
        resetSeenRelease()
    }

    private fun notice(): ChangelogNotice = context.assets.open("changelog.html").bufferedReader().use {
        ChangelogCatalog.parse(it.readText()).notice(BuildConfig.VERSION_NAME, 0)!!
    }

    @Test
    fun rotationKeepsDialogAndCloseAcknowledgesTheRelease() {
        val notice = notice()
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity {
                ChangelogDialogFragment.newInstance(notice).showNow(it.supportFragmentManager, ChangelogDialogFragment.TAG)
            }
            scenario.recreate()
            scenario.onActivity {
                assertEquals(0, ChangelogPreferences(context).lastSeenCode)
                val fragment = it.supportFragmentManager.findFragmentByTag(ChangelogDialogFragment.TAG) as ChangelogDialogFragment
                val dialog = fragment.requireDialog() as AlertDialog
                assertTrue(dialog.isShowing)
                assertEquals(it.getString(R.string.close), dialog.getButton(AlertDialog.BUTTON_NEGATIVE).text.toString())
                assertEquals(it.getString(R.string.app_update_full_changelog), dialog.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                it.supportFragmentManager.executePendingTransactions()
                assertNull(it.supportFragmentManager.findFragmentByTag(ChangelogDialogFragment.TAG))
                assertEquals(notice.code, ChangelogPreferences(context).lastSeenCode)
            }
        }
    }

    @Test
    fun fullHistoryButtonOpensTheBundledPageAndAcknowledgesTheRelease() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(ChangelogActivity::class.java.name, null, false)
        try {
            ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
                scenario.onActivity {
                    val fragment = ChangelogDialogFragment.newInstance(notice())
                    fragment.showNow(it.supportFragmentManager, ChangelogDialogFragment.TAG)
                    (fragment.requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                }
                val page = instrumentation.waitForMonitorWithTimeout(monitor, 5000)
                assertNotNull(page)
                assertEquals(notice().code, ChangelogPreferences(context).lastSeenCode)
                instrumentation.runOnMainSync { page.finish() }
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test
    fun cancelledDialogAcknowledgesButDowngradeCannotResetSeenVersion() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity {
                val fragment = ChangelogDialogFragment.newInstance(notice())
                fragment.showNow(it.supportFragmentManager, ChangelogDialogFragment.TAG)
                fragment.requireDialog().cancel()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val preferences = ChangelogPreferences(context)
            assertEquals(notice().code, preferences.lastSeenCode)
            preferences.acknowledge(1)
            assertEquals(notice().code, preferences.lastSeenCode)
        }
    }

    @Test
    fun fullHistoryActivitySurvivesRecreation() {
        ActivityScenario.launch<ChangelogActivity>(Intent(context, ChangelogActivity::class.java)).use { scenario ->
            scenario.recreate()
            scenario.onActivity {
                assertEquals(it.getString(R.string.changelog_title), it.title.toString())
                assertNotNull(it.findViewById<android.view.View>(R.id.changelog_content))
            }
        }
    }
}
