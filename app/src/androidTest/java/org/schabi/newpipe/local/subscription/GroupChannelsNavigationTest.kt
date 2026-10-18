/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.subscription

import android.Manifest
import android.content.Context
import android.os.Build
import android.view.View
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
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
    fun subscriptionChangesPreserveGroupRowIdentityAndScrollPosition() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val starPromptKey = "github_star_prompt_completed"
        val previousStarPrompt = preferences.all[starPromptKey] as? Boolean
        val changelog = context.getSharedPreferences("changelog", Context.MODE_PRIVATE)
        val previousSeenRelease = changelog.all["last_seen_release"] as? Int
        // This additional launch must neither show prompts nor advance the suite's prompt counter.
        preferences.edit().putBoolean(starPromptKey, true).commit()
        changelog.edit().putInt("last_seen_release", Int.MAX_VALUE).commit()
        val field = NewPipeDatabase::class.java.getDeclaredField("databaseInstance").apply { isAccessible = true }
        val oldDatabase = field.get(null)
        val database = TestDatabase.createReplacingNewPipeDatabase()
        val profile = ProfileManager.getActiveProfileId(context)
        try {
            val members = (0 until 80).map { index ->
                SubscriptionEntity(
                    serviceId = 0,
                    url = "https://www.youtube.com/@scroll-$index",
                    name = "Channel ${index.toString().padStart(3, '0')}",
                    profileId = profile
                ).apply { uid = database.subscriptionDAO().insertIgnore(this) }
            }
            val group = database.feedGroupDAO().insert(FeedGroupEntity(0, "Scroll group", FeedGroupIcon.ALL, profileId = profile))
            database.feedGroupDAO().updateSubscriptionsForGroupForProfile(profile, group, members.map { it.uid })
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    NavigationHelper.openGroupChannelsFragment(activity.supportFragmentManager, group, "Scroll group")
                    activity.supportFragmentManager.executePendingTransactions()
                }
                awaitList(scenario, members.size)
                scenario.onActivity { activity ->
                    val list = activity.findViewById<RecyclerView>(R.id.items_list)
                    assertEquals(members.map { it.uid }, members.indices.map { list.adapter!!.getItemId(it) })
                    (list.layoutManager as GridLayoutManager).scrollToPositionWithOffset(40, 13)
                }
                instrumentation.waitForIdleSync()
                var position = -1
                var offset = 0
                scenario.onActivity { activity ->
                    val layout = activity.findViewById<RecyclerView>(R.id.items_list).layoutManager as GridLayoutManager
                    position = layout.findFirstVisibleItemPosition()
                    offset = layout.findViewByPosition(position)!!.top
                    assertTrue(position > 0)
                }

                // Copying to another profile invalidates Room's shared subscriptions table.
                database.subscriptionDAO().insertIgnore(members[40].copy(uid = 0, profileId = "other-profile"))
                // Also exercise a real content change without changing the channel order.
                database.subscriptionDAO().update(members[0].copy(name = "Channel 000 updated"))
                val deadline = System.currentTimeMillis() + 10000
                var updated = false
                while (!updated && System.currentTimeMillis() < deadline) {
                    scenario.onActivity { activity ->
                        val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) as GroupChannelsFragment
                        updated = ViewModelProvider(fragment)[GroupChannelsViewModel::class.java]
                            .state.value?.channels?.firstOrNull()?.name == "Channel 000 updated"
                    }
                    if (!updated) Thread.sleep(50)
                }
                assertTrue("Updated subscriptions must reach the group", updated)
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val list = activity.findViewById<RecyclerView>(R.id.items_list)
                    val layout = list.layoutManager as GridLayoutManager
                    assertEquals(members.size, list.adapter!!.itemCount)
                    assertEquals(position, layout.findFirstVisibleItemPosition())
                    assertEquals(offset, layout.findViewByPosition(position)!!.top)
                    assertEquals(members[position].uid, list.adapter!!.getItemId(position))
                }
            }
        } finally {
            field.set(null, oldDatabase)
            database.close()
            preferences.edit().apply {
                if (previousStarPrompt == null) remove(starPromptKey) else putBoolean(starPromptKey, previousStarPrompt)
            }.commit()
            changelog.edit().apply {
                if (previousSeenRelease == null) remove("last_seen_release") else putInt("last_seen_release", previousSeenRelease)
            }.commit()
        }
    }

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
