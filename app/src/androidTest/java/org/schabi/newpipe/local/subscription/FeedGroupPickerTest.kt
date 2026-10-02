package org.schabi.newpipe.local.subscription

import android.Manifest
import android.os.Build
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.local.subscription.dialog.FeedGroupDialog
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.testUtil.TestDatabase

class FeedGroupPickerTest {
    @Test
    fun selectionKeepsAvatarVisibleAndDatabaseUpdatesPreserveScroll() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val field = NewPipeDatabase::class.java.getDeclaredField("databaseInstance").apply { isAccessible = true }
        val oldDatabase = field.get(null)
        val database = TestDatabase.createReplacingNewPipeDatabase()
        val profile = ProfileManager.getActiveProfileId(context)
        database.subscriptionDAO().insertAll(
            (0 until 100).map {
                SubscriptionEntity(serviceId = 0, url = "https://www.youtube.com/@picker-$it", name = "Channel %03d".format(it), profileId = profile)
            }
        )
        try {
            ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    FeedGroupDialog.newInstance().showNow(activity.supportFragmentManager, "picker-test")
                    picker(activity).requireView().findViewById<View>(R.id.select_channel_button).performClick()
                }
                await(scenario) { list -> list.adapter?.itemCount == 100 && list.childCount > 0 }
                scenario.onActivity { activity ->
                    (list(activity).layoutManager as GridLayoutManager).scrollToPositionWithOffset(60, 0)
                }
                await(scenario) { list -> (list.layoutManager as GridLayoutManager).findFirstVisibleItemPosition() == 60 }
                var selectedId = -1L
                scenario.onActivity { activity ->
                    val dialog = picker(activity)
                    val row = list(activity).findViewHolderForAdapterPosition(60)!!.itemView
                    row.performClick()
                    selectedId = dialog.selectedSubscriptions.single()
                    assertTrue(row.isSelected)
                    val badge = row.findViewById<View>(R.id.selected_highlight)
                    val avatar = row.findViewById<View>(R.id.thumbnail_view)
                    assertTrue(avatar.isShown)
                    assertTrue("Selection badge must not cover the avatar", badge.width < avatar.width && badge.height < avatar.height)
                }
                instrumentation.waitForIdleSync()
                var firstVisible = -1
                scenario.onActivity { activity ->
                    firstVisible = (list(activity).layoutManager as GridLayoutManager).findFirstVisibleItemPosition()
                }
                database.subscriptionDAO().insertIgnore(
                    SubscriptionEntity(serviceId = 0, url = "https://www.youtube.com/@picker-new", name = "ZZ new channel", profileId = profile)
                )
                await(scenario) { list -> list.adapter?.itemCount == 101 }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    assertEquals(firstVisible, (list(activity).layoutManager as GridLayoutManager).findFirstVisibleItemPosition())
                    assertEquals(setOf(selectedId), picker(activity).selectedSubscriptions)
                    picker(activity).dismissNow()
                }
            }
        } finally {
            field.set(null, oldDatabase)
            database.close()
        }
    }

    private fun picker(activity: AboutActivity) = activity.supportFragmentManager.findFragmentByTag("picker-test") as FeedGroupDialog

    private fun list(activity: AboutActivity) = picker(activity).requireView().findViewById<RecyclerView>(R.id.subscriptions_selector_list)

    private fun await(scenario: ActivityScenario<AboutActivity>, condition: (RecyclerView) -> Boolean) {
        val deadline = System.currentTimeMillis() + 10000
        while (System.currentTimeMillis() < deadline) {
            var ready = false
            scenario.onActivity { ready = condition(list(it)) }
            if (ready) return
            Thread.sleep(50)
        }
        throw AssertionError("Subscription picker did not reach expected state")
    }
}
