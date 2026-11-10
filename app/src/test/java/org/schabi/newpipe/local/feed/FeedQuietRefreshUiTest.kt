package org.schabi.newpipe.local.feed

import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
import java.time.Duration
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.schabi.newpipe.App
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.StreamWithState
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.learning.VideoNoteBadges
import org.schabi.newpipe.local.feed.item.StreamItem
import org.schabi.newpipe.local.feed.preview.FeedPreviewController
import org.schabi.newpipe.local.feed.quickadd.FeedQuickAddController
import org.schabi.newpipe.util.BridgeStateSaverInitializer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FeedQuietRefreshUiTest {
    private val baseline = OffsetDateTime.parse("2026-11-01T10:00:00Z")

    @Test
    fun channelBatchesStaySilentAndDoNotRebindUnchangedRows() = withFeed { fragment, states ->
        val list = fragment.requireView().findViewById<RecyclerView>(R.id.items_list)
        val banner = fragment.requireView().findViewById<View>(R.id.new_items_loaded_button)
        val cached = listOf(item(1, baseline.minusDays(1)))
        states.value = loaded(cached)
        await { list.adapter!!.itemCount == 1 }
        val animator = list.itemAnimator
        var fullUpdates = 0
        var changedRows = 0
        list.adapter!!.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                fullUpdates++
            }
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                changedRows += itemCount
            }
        })
        states.value = loaded(cached, FeedState.ProgressState(0, 3))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        assertNull(list.itemAnimator)
        assertFalse(banner.isVisible)
        var items = cached
        for (id in 2L..3L) {
            items = items + item(id, baseline.plusMinutes(id))
            states.value = loaded(items, FeedState.ProgressState((id - 1).toInt(), 3))
            await { list.adapter!!.itemCount == id.toInt() }
            assertFalse(banner.isVisible)
        }
        assertEquals(0, fullUpdates)
        assertEquals(0, changedRows)
        val notice = FeedRefreshNotice(setOf(1L))
        states.value = loaded(items, notice = notice)
        System.gc() // Groupie must not lose the final callback under memory pressure.
        await { banner.isVisible }
        assertSame(animator, list.itemAnimator)
        banner.performClick()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        assertFalse(banner.isVisible)
        // Room can deliver another content snapshot after completion. It must
        // neither restart the banner nor rebind all previously displayed rows.
        items = items + item(4, baseline.plusMinutes(4))
        states.value = loaded(items, notice = notice)
        await { list.adapter!!.itemCount == 4 }
        assertFalse(banner.isVisible)
        assertEquals(0, fullUpdates)
        assertEquals(0, changedRows)
    }

    @Test
    fun completionWaitsForLateNewItemsAndCancellationStaysSilent() = withFeed { fragment, states ->
        val list = fragment.requireView().findViewById<RecyclerView>(R.id.items_list)
        val banner = fragment.requireView().findViewById<View>(R.id.new_items_loaded_button)
        val cached = listOf(item(1, baseline.minusDays(1)))
        states.value = loaded(cached, FeedState.ProgressState(0, 1))
        await { list.adapter!!.itemCount == 1 }
        states.value = loaded(cached) // Cancelled: idle without success.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        assertFalse(banner.isVisible)
        val notice = FeedRefreshNotice(setOf(1L))
        states.value = loaded(cached, notice = notice)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        assertFalse(banner.isVisible)
        states.value = loaded(cached + item(2, baseline.plusHours(1)), notice = notice)
        await { banner.isVisible }
        assertEquals(2, list.adapter!!.itemCount)
    }

    @Test
    fun highlightsParticipateInDiffEqualityWithoutMutatingTheCachedItem() {
        val cached = item(1, baseline)
        val first = cached.copy(isHighlighted = true)
        val unchanged = cached.copy(isHighlighted = true)
        assertEquals(first, unchanged)
        assertNotEquals(first, cached.copy(isHighlighted = false))
        assertFalse(cached.isHighlighted)
    }

    private fun loaded(items: List<StreamItem>, progress: FeedState.ProgressState? = null, notice: FeedRefreshNotice? = null) = FeedState.LoadedState(items, baseline, 0, emptyList(), progress, baseline, notice)

    private fun item(id: Long, date: OffsetDateTime) = StreamItem(
        StreamWithState(
            StreamEntity(uid = id, serviceId = 0, url = "https://example.com/video/$id", title = "Video $id", streamType = StreamType.VIDEO_STREAM, duration = 60, uploader = "Channel", uploadDate = date),
            null
        )
    )

    private fun await(condition: () -> Boolean) {
        repeat(500) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10))
            if (condition()) return
            Thread.sleep(10)
        }
        assertTrue("Feed diff did not finish", condition())
    }

    private fun withFeed(test: (FeedFragment, MutableLiveData<FeedState>) -> Unit) {
        // Keep the completion banner available to inspect. Robolectric can finish
        // property animations synchronously, including their delayed hide callback.
        android.provider.Settings.System.putFloat(
            RuntimeEnvironment.getApplication().contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
        BridgeStateSaverInitializer.init(RuntimeEnvironment.getApplication())
        org.schabi.newpipe.util.StateSaver.init(RuntimeEnvironment.getApplication())
        org.schabi.newpipe.util.Localization.initPrettyTime(org.ocpsoft.prettytime.PrettyTime(java.util.Locale.US))
        val state = MutableLiveData<FeedState>()
        val instance = App::class.java.getDeclaredField("instance").apply { isAccessible = true }
        val previous = instance.get(null)
        val app = mock(App::class.java)
        `when`(app.packageManager).thenReturn(RuntimeEnvironment.getApplication().packageManager)
        instance.set(null, app)
        try {
            mockConstruction(FeedViewModel::class.java) { model, _ ->
                `when`(model.stateLiveData).thenReturn(state)
                `when`(model.getShowPlayedItemsFromPreferences()).thenReturn(true)
                `when`(model.getShowPartiallyPlayedItemsFromPreferences()).thenReturn(true)
                `when`(model.getShowFutureItemsFromPreferences()).thenReturn(true)
            }.use {
                mockConstruction(VideoNoteBadges::class.java).use {
                    mockConstruction(FeedPreviewController::class.java).use {
                        mockConstruction(FeedQuickAddController::class.java).use {
                            mockConstruction(FeedGroupSelector::class.java).use {
                                val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
                                controller.get().setTheme(R.style.LightTheme)
                                controller.setup()
                                var failure: Throwable? = null
                                try {
                                    val fragment = FeedFragment.newInstance()
                                    controller.get().supportFragmentManager.beginTransaction()
                                        .add(android.R.id.content, fragment).commitNow()
                                    test(fragment, state)
                                } catch (error: Throwable) {
                                    failure = error
                                    throw error
                                } finally {
                                    try {
                                        controller.pause().stop().destroy()
                                    } catch (cleanupError: Throwable) {
                                        if (failure == null) throw cleanupError
                                        failure.addSuppressed(cleanupError)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            instance.set(null, previous)
        }
    }
}
