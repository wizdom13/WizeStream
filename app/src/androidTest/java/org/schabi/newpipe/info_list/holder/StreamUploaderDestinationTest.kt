/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.info_list.holder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import java.io.IOException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.fragments.detail.VideoDetailFragment
import org.schabi.newpipe.fragments.list.channel.ChannelFragment
import org.schabi.newpipe.fragments.list.search.SearchFragment
import org.schabi.newpipe.fragments.list.videos.RelatedItemsFragment
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.settings.tabs.Tab
import org.schabi.newpipe.settings.tabs.TabsManager
import org.schabi.newpipe.util.InfoCache
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ServiceHelper

/** Exercise the real activity, list callbacks, fragment transaction and player-sheet listener. */
@RunWith(AndroidJUnit4::class)
class StreamUploaderDestinationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var savedPreferences: Map<String, Any?>
    private lateinit var restoreDownloader: () -> Unit

    @Before
    fun configureOfflineNavigation() {
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val keys = listOf(
            R.string.current_service_key,
            R.string.saved_tabs_key,
            R.string.show_comments_key,
            R.string.show_next_video_key,
            R.string.show_description_key,
            R.string.stream_info_selected_tab_key,
            R.string.list_view_mode_key,
            R.string.list_view_mode_portrait_key,
            R.string.list_view_mode_landscape_key,
            R.string.image_quality_key
        ).map(context::getString) + "github_star_prompt_dismissed"
        savedPreferences = keys.associateWith { preferences.all[it] }
        TabsManager.getManager(context).saveTabs(listOf(Tab.Type.BLANK.tab))
        ServiceHelper.setSelectedServiceId(context, 0)
        preferences.edit()
            .putBoolean("github_star_prompt_dismissed", true)
            .putBoolean(context.getString(R.string.show_comments_key), false)
            .putBoolean(context.getString(R.string.show_next_video_key), true)
            .putBoolean(context.getString(R.string.show_description_key), false)
            .putString(context.getString(R.string.list_view_mode_key), "list")
            .putString(context.getString(R.string.list_view_mode_portrait_key), "list")
            .putString(context.getString(R.string.list_view_mode_landscape_key), "list")
            .putString(context.getString(R.string.image_quality_key), context.getString(R.string.image_quality_none_key))
            .commit()

        val downloader = NewPipe.getDownloader()
        val localization = NewPipe.getPreferredLocalization()
        val country = NewPipe.getPreferredContentCountry()
        restoreDownloader = { NewPipe.init(downloader, localization, country) }
        NewPipe.init(
            object : Downloader() {
                override fun execute(request: Request): Response = throw IOException("Navigation fixtures are cached locally")
                override fun executeAsync(request: Request, callback: AsyncCallback): Nothing = throw IOException("Navigation fixtures are cached locally")
            },
            localization,
            country
        )
        InfoCache.getInstance().clearCache()
    }

    @After
    fun restoreConfiguration() {
        restoreDownloader()
        InfoCache.getInstance().clearCache()
        preferences.edit().apply {
            savedPreferences.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    else -> remove(key)
                }
            }
        }.commit()
    }

    @Test(timeout = 60_000)
    fun globalSearchUploaderTapOpensOneChannelWithoutOpeningAVideo() {
        launch().use { scenario ->
            var playerBefore: androidx.fragment.app.Fragment? = null
            var backStackBefore = 0
            scenario.onActivity { activity ->
                NavigationHelper.openSearchFragment(activity.supportFragmentManager, 0, "")
                activity.supportFragmentManager.executePendingTransactions()
            }
            // Fragment enter animations defer onResume; that callback opens the suggestions.
            // Wait for it before rendering and dismissing, so it cannot reopen them after Back.
            waitFor(scenario, "Search did not resume and open its suggestions") { activity ->
                val search = activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) as? SearchFragment
                search?.isResumed == true && search.view?.findViewById<View>(R.id.suggestions_panel)?.visibility == View.VISIBLE
            }
            scenario.onActivity { activity ->
                val search = activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) as SearchFragment
                // Render an offline response, then use the fragment's existing Back transition
                // to dismiss the initial suggestions and keyboard before touching its result.
                search.handleResult(
                    SearchInfo(0, SearchQueryHandler("", "", "fixture", emptyList(), null), "fixture").apply {
                        relatedItems = listOf(stream())
                        metaInfo = emptyList()
                    }
                )
                assertTrue("The resumed search must dismiss its suggestions after receiving a result", search.onBackPressed())
                playerBefore = activity.supportFragmentManager.findFragmentById(R.id.fragment_player_holder)
                backStackBefore = activity.supportFragmentManager.backStackEntryCount
            }
            waitFor(scenario) { activity ->
                val search = activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) as? SearchFragment
                search?.view?.findViewById<View>(R.id.suggestions_panel)?.visibility == View.GONE &&
                    searchList(activity)?.let(::channelTarget) != null &&
                    ViewCompat.getRootWindowInsets(activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) != true
            }
            scenario.onActivity { activity ->
                cacheChannel()
                tap(activity, requireNotNull(channelTarget(requireNotNull(searchList(activity)))))
            }
            waitFor(scenario) { activity -> activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) is ChannelFragment }
            scenario.onActivity { activity ->
                assertSame(playerBefore, activity.supportFragmentManager.findFragmentById(R.id.fragment_player_holder))
                assertEquals(backStackBefore + 1, activity.supportFragmentManager.backStackEntryCount)
                assertTrue(activity.supportFragmentManager.findFragmentById(R.id.fragment_holder)!!.isVisible)
            }
        }
    }

    @Test(timeout = 60_000)
    fun relatedUploaderTapRevealsChannelAndCollapsesThePlayerWithoutChangingItsVideoOrQueue() {
        val info = StreamInfo(0, "current", CURRENT_VIDEO, "Current video").apply {
            streamType = StreamType.VIDEO_STREAM
            uploaderName = "Fixture channel"
            uploaderUrl = CHANNEL
            duration = 120
            relatedItems = listOf(stream())
        }
        val queue = SinglePlayQueue(info)
        try {
            launch().use { scenario ->
                lateinit var detail: VideoDetailFragment
                var backStackBefore = 0
                waitFor(scenario, "Home did not finish opening before the player fixture") { activity ->
                    activity.supportFragmentManager.findFragmentById(R.id.fragment_holder)?.isResumed == true
                }
                scenario.onActivity { activity ->
                    // Seed after startup, immediately before the real detail load. A blank
                    // home avoids unrelated feed errors evicting the shared info cache.
                    InfoCache.getInstance().putInfo(0, CURRENT_VIDEO, info, InfoCache.Type.STREAM)
                    detail = VideoDetailFragment.getInstance(0, CURRENT_VIDEO, info.name, queue).apply { setAutoPlay(false) }
                    activity.supportFragmentManager.beginTransaction().replace(R.id.fragment_player_holder, detail).commit()
                    activity.supportFragmentManager.executePendingTransactions()
                    backStackBefore = activity.supportFragmentManager.backStackEntryCount
                }
                waitFor(scenario, "The detail fragment did not resume", { detailState(it, detail) }) {
                    detail.isResumed
                }
                scenario.onActivity { activity ->
                    // Arrange the gesture's starting state once, independently of startup
                    // back-stack notifications. The tap must cause the subsequent collapse.
                    sheet(activity).state = BottomSheetBehavior.STATE_EXPANDED
                }
                waitFor(scenario, "The player sheet did not expand", { detailState(it, detail) }) { activity ->
                    sheet(activity).state == BottomSheetBehavior.STATE_EXPANDED
                }
                waitFor(scenario, "The detail fragment did not create its related list", { detailState(it, detail) }) {
                    relatedList(detail) != null
                }
                scenario.onActivity {
                    // Bring the related section into view on short screens, as when scrolling
                    // past the video header; the outer player sheet must remain expanded.
                    detail.requireView().findViewById<AppBarLayout>(R.id.app_bar_layout).setExpanded(false, false)
                }
                waitFor(scenario, "The related uploader did not become visible after scrolling the video header", { detailState(it, detail) }) { activity ->
                    sheet(activity).state == BottomSheetBehavior.STATE_EXPANDED && relatedList(detail)?.let(::channelTarget) != null
                }
                scenario.onActivity { activity ->
                    cacheChannel()
                    tap(activity, requireNotNull(channelTarget(requireNotNull(relatedList(detail)))))
                }
                waitFor(scenario) { activity ->
                    activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) is ChannelFragment && sheet(activity).state == BottomSheetBehavior.STATE_COLLAPSED
                }
                scenario.onActivity { activity ->
                    assertSame(detail, activity.supportFragmentManager.findFragmentById(R.id.fragment_player_holder))
                    assertEquals(CURRENT_VIDEO, detail.url)
                    val queueField = VideoDetailFragment::class.java.getDeclaredField("playQueue").apply { isAccessible = true }
                    assertSame(queue, queueField.get(detail))
                    assertEquals(CURRENT_VIDEO, queue.item!!.url)
                    assertEquals(backStackBefore + 1, activity.supportFragmentManager.backStackEntryCount)
                    assertTrue(activity.supportFragmentManager.findFragmentById(R.id.fragment_holder)!!.isVisible)
                }
            }
        } finally {
            queue.dispose()
        }
    }

    private fun launch(): ActivityScenario<MainActivity> = ActivityScenario.launch(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW))

    private fun cacheChannel() {
        val channel = ChannelInfo(0, "fixture", CHANNEL, CHANNEL, "Fixture channel", ListLinkHandler(CHANNEL, CHANNEL, "fixture", emptyList(), null))
        InfoCache.getInstance().putInfo(0, CHANNEL, channel, InfoCache.Type.CHANNEL)
    }

    private fun stream() = StreamInfoItem(0, RELATED_VIDEO, "Related fixture video", StreamType.VIDEO_STREAM).apply {
        uploaderName = "Fixture channel"
        uploaderUrl = CHANNEL
        viewCount = 12
    }

    private fun searchList(activity: MainActivity): RecyclerView? = (activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) as? SearchFragment)
        ?.view?.findViewById(R.id.items_list)

    private fun relatedList(detail: VideoDetailFragment): RecyclerView? = detail.childFragmentManager.fragments.filterIsInstance<RelatedItemsFragment>()
        .firstOrNull()?.view?.findViewById(R.id.items_list)

    private fun channelTarget(list: RecyclerView): View? = (0 until list.childCount).asSequence()
        .mapNotNull { list.getChildAt(it).findViewById<View>(R.id.itemUploaderLink) }
        .firstOrNull { target ->
            val visible = Rect()
            val location = IntArray(2)
            target.getLocationOnScreen(location)
            target.isShown && target.width > 0 && target.height > 0 && target.getGlobalVisibleRect(visible) &&
                visible.contains(location[0] + target.width / 2, location[1] + target.height / 2)
        }

    private fun sheet(activity: MainActivity): BottomSheetBehavior<FrameLayout> = BottomSheetBehavior.from(activity.findViewById(R.id.fragment_player_holder))

    private fun detailState(activity: MainActivity, detail: VideoDetailFragment): String = "added=${detail.isAdded}, resumed=${detail.isResumed}, " +
        "current=${activity.supportFragmentManager.findFragmentById(R.id.fragment_player_holder) === detail}, " +
        "sheet=${sheet(activity).state}, related=${detail.isAdded && relatedList(detail) != null}, " +
        "error=${detail.view?.let { detail.isErrorPanelVisible }}, url=${detail.url}, " +
        "cached=${InfoCache.getInstance().getFromKey(0, CURRENT_VIDEO, InfoCache.Type.STREAM) != null}"

    private fun tap(activity: MainActivity, target: View) {
        val location = IntArray(2)
        target.getLocationInWindow(location)
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, down + if (action == MotionEvent.ACTION_UP) 32 else 0, action, location[0] + target.width / 2f, location[1] + target.height / 2f, 0)
            try {
                activity.dispatchTouchEvent(event)
            } finally {
                event.recycle()
            }
        }
    }

    private fun waitFor(
        scenario: ActivityScenario<MainActivity>,
        failureMessage: String = "The expected channel destination or player state was not reached",
        diagnostics: (MainActivity) -> String = { "" },
        predicate: (MainActivity) -> Boolean
    ) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            var ready = false
            scenario.onActivity { ready = predicate(it) }
            if (ready) return
            SystemClock.sleep(50)
        }
        var state = ""
        scenario.onActivity { state = diagnostics(it) }
        throw AssertionError(if (state.isEmpty()) failureMessage else "$failureMessage ($state)")
    }

    companion object {
        private const val CHANNEL = "https://www.youtube.com/channel/UCfixture"
        private const val CURRENT_VIDEO = "https://www.youtube.com/watch?v=Current0001"
        private const val RELATED_VIDEO = "https://www.youtube.com/watch?v=Related0001"
    }
}
