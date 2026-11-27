/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments.list.channel

import android.app.Application
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.processors.BehaviorProcessor
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.local.feed.service.ChannelFeedRefreshKey
import org.schabi.newpipe.local.feed.service.ChannelFeedRefreshRequest
import org.schabi.newpipe.local.feed.service.ChannelFeedRefreshState
import org.schabi.newpipe.local.feed.service.ChannelFeedRefreshViewModel
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.ServiceHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ChannelFeedRefreshMenuTest {
    private val application get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
        PreferenceManager.getDefaultSharedPreferences(application).edit().clear().commit()
        ServiceHelper.setSelectedServiceId(application, 0)
        ProfileManager.getActiveProfileId(application)
    }

    @After
    fun tearDown() {
        RxJavaPlugins.reset()
        RxAndroidPlugins.reset()
    }

    @Test
    fun menuRequiresExactSubscribedIdentityButIncludesMutedAndOtherSavedMode() = withScreen { screen ->
        val item = screen.menu().findItem(R.id.menu_item_refresh_channel_feed)
        assertFalse(item.isVisible)

        screen.subscriptions.onNext(listOf(subscription().copy(profileId = "another-profile")))
        assertFalse(item.isVisible)
        screen.subscriptions.onNext(listOf(subscription().copy(url = URL.replace('_', 'X'))))
        assertFalse(item.isVisible)

        val mutedMusic = subscription().copy(feedMuted = true, youtubeModeMask = SubscriptionEntity.YOUTUBE_MODE_MUSIC)
        screen.subscriptions.onNext(listOf(mutedMusic))
        assertTrue(item.isVisible)
        assertTrue(item.isEnabled)
        screen.helper.onMenuItemSelected(item)
        verify(screen.model).refresh(ChannelFeedRefreshRequest.from(mutedMusic))
        assertTrue(mutedMusic.feedMuted)
    }

    @Test
    fun runningRefreshDisablesDuplicateClicksAndShowsProgress() = withScreen { screen ->
        val subscription = subscription()
        screen.subscriptions.onNext(listOf(subscription))
        val item = screen.menu().findItem(R.id.menu_item_refresh_channel_feed)
        val request = ChannelFeedRefreshRequest.from(subscription)
        screen.helper.onMenuItemSelected(item)
        verify(screen.model).refresh(request)

        screen.states.value = mapOf(request.key() to ChannelFeedRefreshState.Running(request))
        assertFalse(item.isEnabled)
        assertEquals(screen.activity.getString(R.string.channel_feed_refresh_running), item.title)
        screen.helper.onMenuItemSelected(item)
        verify(screen.model, times(1)).refresh(request)
        assertEquals(screen.activity.getString(R.string.channel_feed_refresh_running), screen.snackbarText())
    }

    @Test
    fun completionMessagesDistinguishSuccessPartialFailureAndChangedSubscription() = withScreen { screen ->
        val subscription = subscription()
        screen.subscriptions.onNext(listOf(subscription))
        val request = ChannelFeedRefreshRequest.from(subscription)
        val messages = mapOf(
            ChannelFeedRefreshState.Outcome.SUCCESS to R.string.channel_feed_refresh_complete,
            ChannelFeedRefreshState.Outcome.PARTIAL to R.string.channel_feed_refresh_partial,
            ChannelFeedRefreshState.Outcome.FAILED to R.string.channel_feed_refresh_failed,
            ChannelFeedRefreshState.Outcome.UNAVAILABLE to R.string.channel_feed_refresh_unavailable
        )
        messages.forEach { (outcome, message) ->
            val result = ChannelFeedRefreshState.Finished(request, outcome)
            screen.states.value = mapOf(request.key() to result)
            assertEquals(screen.activity.getString(message), screen.snackbarText())
            verify(screen.model).consume(result)
            assertTrue(screen.states.value.orEmpty().isEmpty())
        }
    }

    @Test
    fun unsubscribeAndProfileSwitchHideActionAndRejectStaleClicks() = withScreen { screen ->
        screen.subscriptions.onNext(listOf(subscription()))
        val item = screen.menu().findItem(R.id.menu_item_refresh_channel_feed)
        assertTrue(item.isVisible)
        screen.subscriptions.onNext(emptyList())
        assertFalse(item.isVisible)
        screen.helper.onMenuItemSelected(item)
        verify(screen.model, never()).refresh(ChannelFeedRefreshRequest.from(subscription()))

        screen.subscriptions.onNext(listOf(subscription()))
        assertTrue(item.isVisible)
        val other = requireNotNull(ProfileManager.createProfile(application, "Other profile"))
        assertEquals(other.id, ProfileManager.getActiveProfileId(application))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(item.isVisible)
        assertFalse(screen.subscriptions.hasSubscribers())
        screen.helper.onMenuItemSelected(item)
        verify(screen.model, never()).refresh(ChannelFeedRefreshRequest.from(subscription()))
    }

    @Test
    fun pausedPageLeavesFinishedResultForItsNextResume() = withScreen { screen ->
        screen.subscriptions.onNext(listOf(subscription()))
        val request = ChannelFeedRefreshRequest.from(subscription())
        val result = ChannelFeedRefreshState.Finished(request, ChannelFeedRefreshState.Outcome.SUCCESS)
        screen.host.pause()
        screen.states.value = mapOf(request.key() to result)
        verify(screen.model, never()).consume(result)

        screen.host.resume()
        assertEquals(screen.activity.getString(R.string.channel_feed_refresh_complete), screen.snackbarText())
        verify(screen.model, times(1)).consume(result)
        screen.host.pause().resume()
        verify(screen.model, times(1)).consume(result)
    }

    @Test
    fun expandedPlayerDefersResultUntilCollapseWithoutPausingChannel() = withScreen(withPlayer = true) { screen ->
        screen.subscriptions.onNext(listOf(subscription()))
        val request = ChannelFeedRefreshRequest.from(subscription())
        val result = ChannelFeedRefreshState.Finished(request, ChannelFeedRefreshState.Outcome.SUCCESS)
        val behavior = requireNotNull(screen.playerBehavior)
        val player = requireNotNull(screen.playerSheet)
        behavior.dispatchState(player, BottomSheetBehavior.STATE_EXPANDED)
        assertEquals(Lifecycle.State.RESUMED, screen.fragment.viewLifecycleOwner.lifecycle.currentState)

        screen.states.value = mapOf(request.key() to result)
        verify(screen.model, never()).consume(result)
        behavior.dispatchState(player, BottomSheetBehavior.STATE_SETTLING)
        verify(screen.model, never()).consume(result)
        behavior.dispatchState(player, BottomSheetBehavior.STATE_COLLAPSED)

        assertEquals(Lifecycle.State.RESUMED, screen.fragment.viewLifecycleOwner.lifecycle.currentState)
        assertEquals(screen.activity.getString(R.string.channel_feed_refresh_complete), screen.snackbarText())
        verify(screen.model, times(1)).consume(result)
        behavior.dispatchState(player, BottomSheetBehavior.STATE_HIDDEN)
        verify(screen.model, times(1)).consume(result)
    }

    @Test
    fun removingChannelViewRemovesMenuAndSubscriptionAndPlayerObservers() = withScreen(withPlayer = true) { screen ->
        screen.subscriptions.onNext(listOf(subscription()))
        assertTrue(screen.menu().findItem(R.id.menu_item_refresh_channel_feed).isVisible)
        assertTrue(screen.subscriptions.hasSubscribers())
        assertEquals(1, requireNotNull(screen.playerBehavior).callbacks.size)
        screen.activity.supportFragmentManager.beginTransaction().remove(screen.fragment).commitNow()
        assertNull(screen.menu().findItem(R.id.menu_item_refresh_channel_feed))
        assertFalse(screen.subscriptions.hasSubscribers())
        assertTrue(screen.playerBehavior.callbacks.isEmpty())
    }

    private fun withScreen(withPlayer: Boolean = false, test: (Screen) -> Unit) {
        val subscriptions = BehaviorProcessor.createDefault<List<SubscriptionEntity>>(emptyList())
        val states = MutableLiveData<Map<ChannelFeedRefreshKey, ChannelFeedRefreshState>>(emptyMap())
        mockConstruction(SubscriptionManager::class.java) { manager, _ ->
            `when`(manager.getSubscriptionFlowable(0, URL)).thenReturn(subscriptions)
        }.use {
            mockConstruction(ChannelFeedRefreshViewModel::class.java) { model, _ ->
                `when`(model.states).thenReturn(states)
                doAnswer { invocation ->
                    val result = invocation.getArgument<ChannelFeedRefreshState.Finished>(0)
                    if (states.value?.get(result.request.key()) === result) {
                        states.value = states.value.orEmpty() - result.request.key()
                    }
                    null
                }.`when`(model).consume(
                    any(ChannelFeedRefreshState.Finished::class.java) ?: ChannelFeedRefreshState.Finished(
                        ChannelFeedRefreshRequest.from(subscription()),
                        ChannelFeedRefreshState.Outcome.SUCCESS
                    )
                )
            }.use { models ->
                val host = Robolectric.buildActivity(AppCompatActivity::class.java)
                val activity = host.get()
                activity.setTheme(R.style.LightTheme)
                host.setup()
                try {
                    val playerHost = if (withPlayer) installPlayerHost(activity) else null
                    val fragment = ChannelPage()
                    val channelHolder = if (withPlayer) R.id.fragment_holder else android.R.id.content
                    activity.supportFragmentManager.beginTransaction().add(channelHolder, fragment).commitNow()
                    val helper = ChannelFeedRefreshMenu.attach(fragment)
                    val info = mock(ChannelInfo::class.java)
                    `when`(info.serviceId).thenReturn(0)
                    `when`(info.url).thenReturn(URL)
                    helper.bind(info)
                    test(Screen(host, activity, fragment, helper, models.constructed().single(), subscriptions, states, playerHost?.first, playerHost?.second))
                } finally {
                    host.pause().stop().destroy()
                }
            }
        }
    }

    class ChannelPage : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View = CoordinatorLayout(requireContext())
    }

    private fun installPlayerHost(activity: AppCompatActivity): Pair<FrameLayout, TrackingSheetBehavior> {
        val root = CoordinatorLayout(activity)
        root.addView(FrameLayout(activity).apply { id = R.id.fragment_holder }, CoordinatorLayout.LayoutParams(-1, -1))
        val behavior = TrackingSheetBehavior()
        val player = FrameLayout(activity).apply {
            id = R.id.fragment_player_holder
            addView(View(activity))
        }
        root.addView(player, CoordinatorLayout.LayoutParams(-1, -1).apply { this.behavior = behavior })
        activity.setContentView(root)
        return player to behavior
    }

    private class TrackingSheetBehavior : BottomSheetBehavior<FrameLayout>() {
        val callbacks = mutableListOf<BottomSheetCallback>()

        override fun addBottomSheetCallback(callback: BottomSheetCallback) {
            super.addBottomSheetCallback(callback)
            callbacks += callback
        }

        override fun removeBottomSheetCallback(callback: BottomSheetCallback) {
            super.removeBottomSheetCallback(callback)
            callbacks -= callback
        }

        fun dispatchState(sheet: View, state: Int) = callbacks.toList().forEach { it.onStateChanged(sheet, state) }
    }

    private data class Screen(
        val host: ActivityController<AppCompatActivity>,
        val activity: AppCompatActivity,
        val fragment: Fragment,
        val helper: ChannelFeedRefreshMenu,
        val model: ChannelFeedRefreshViewModel,
        val subscriptions: BehaviorProcessor<List<SubscriptionEntity>>,
        val states: MutableLiveData<Map<ChannelFeedRefreshKey, ChannelFeedRefreshState>>,
        val playerSheet: FrameLayout?,
        val playerBehavior: TrackingSheetBehavior?
    ) {
        fun menu() = MenuBuilder(activity).also { activity.onCreatePanelMenu(Window.FEATURE_OPTIONS_PANEL, it) }

        fun snackbarText(): String? {
            val decor = activity.window.decorView
            decor.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
            )
            decor.layout(0, 0, 1080, 1920)
            shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS)
            return activity.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.text?.toString()
        }
    }

    private fun subscription() = SubscriptionEntity(
        uid = 11,
        serviceId = 0,
        url = URL,
        name = "Channel fixture",
        profileId = ProfileManager.DEFAULT_PROFILE_ID
    )

    private fun ChannelFeedRefreshRequest.key() = ChannelFeedRefreshKey(profileId, subscriptionId)

    companion object {
        private const val URL = "https://www.youtube.com/channel/UC_test"
    }
}
