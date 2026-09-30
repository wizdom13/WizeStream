/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.subscription

import android.Manifest
import android.os.Build
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.fragments.list.channel.ChannelFragment
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.testUtil.TestDatabase
import org.schabi.newpipe.util.NavigationHelper

class GroupChannelsNavigationTest {
    @Test
    fun groupMembersAreVisibleAfterOpeningAndRecreationAndEmptyGroupsShowAMessage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val field = NewPipeDatabase::class.java.getDeclaredField("databaseInstance").apply { isAccessible = true }
        val oldDatabase = field.get(null)
        val database = TestDatabase.createReplacingNewPipeDatabase()
        val profile = ProfileManager.getActiveProfileId(context)
        val member = database.subscriptionDAO().insertIgnore(SubscriptionEntity(serviceId = 0, url = "https://www.youtube.com/@group-member", name = "Group member", profileId = profile))
        database.subscriptionDAO().insertIgnore(SubscriptionEntity(serviceId = 0, url = "https://www.youtube.com/@outside", name = "Outside group", profileId = profile))
        database.subscriptionDAO().insertIgnore(SubscriptionEntity(serviceId = 0, url = "https://www.youtube.com/@other-profile", name = "Other profile", profileId = "other-profile"))
        val group = database.feedGroupDAO().insert(FeedGroupEntity(0, "Test group", FeedGroupIcon.ALL, profileId = profile))
        database.feedGroupDAO().updateSubscriptionsForGroupForProfile(profile, group, listOf(member))
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    NavigationHelper.openGroupChannelsFragment(activity.supportFragmentManager, group, "Test group")
                    activity.supportFragmentManager.executePendingTransactions()
                }
                awaitList(scenario, 1)
                scenario.recreate()
                awaitList(scenario, 1)
                scenario.onActivity { activity ->
                    val list = activity.findViewById<RecyclerView>(R.id.items_list)
                    assertEquals("Group member", list.getChildAt(0).findViewById<TextView>(R.id.itemTitleView).text.toString())
                }
                database.feedGroupDAO().updateSubscriptionsForGroupForProfile(profile, group, emptyList())
                awaitList(scenario, 0)
                scenario.onActivity { activity ->
                    assertTrue(activity.findViewById<View>(R.id.empty_state_view).isShown)
                    assertTrue(activity.findViewById<View>(R.id.empty_state_message).isShown)
                }
                database.feedGroupDAO().updateSubscriptionsForGroupForProfile(profile, group, listOf(member))
                awaitList(scenario, 1)
                scenario.onActivity { activity ->
                    val row = activity.findViewById<RecyclerView>(R.id.items_list).getChildAt(0)
                    assertTrue("Channel rows must support TV focus", row.isFocusable)
                    assertTrue(row.performClick())
                    activity.supportFragmentManager.executePendingTransactions()
                    assertTrue(activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) is ChannelFragment)
                    activity.supportFragmentManager.popBackStackImmediate()
                }
                awaitList(scenario, 1)
            }
        } finally {
            field.set(null, oldDatabase)
            database.close()
        }
    }

    private fun awaitList(scenario: ActivityScenario<MainActivity>, count: Int) {
        val deadline = System.currentTimeMillis() + 10000
        while (System.currentTimeMillis() < deadline) {
            var ready = false
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) as? GroupChannelsFragment
                val list = fragment?.view?.findViewById<RecyclerView>(R.id.items_list)
                ready = list?.adapter?.itemCount == count && if (count == 0) {
                    fragment?.view?.findViewById<View>(R.id.empty_state_view)?.isShown == true
                } else {
                    list?.isShown == true && list.childCount > 0
                }
            }
            if (ready) return
            Thread.sleep(50)
        }
        throw AssertionError("Group channel list did not display $count items")
    }
}
