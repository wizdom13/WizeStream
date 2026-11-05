package org.schabi.newpipe.local.feed.preview

import android.os.SystemClock
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.reactivex.rxjava3.core.Single
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType

@RunWith(AndroidJUnit4::class)
class FeedPreviewControllerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val stream = StreamEntity(serviceId = 0, url = "https://www.youtube.com/watch?v=preview", title = "Preview", streamType = StreamType.VIDEO_STREAM, duration = 4, uploader = "Test")

    @Test
    fun requiresOptInEligibleCardAndExplicitTapWithoutAnotherPlayer() {
        val loads = AtomicInteger()
        withList({
            loads.incrementAndGet()
            Single.never()
        }, busy = true) { scenario, list, controller ->
            scenario.onActivity {
                val row = list.getChildAt(0)
                preference(false)
                controller.bind(row, stream, true)
                assertNull(row.findViewById<View>(R.id.feed_preview_button))
                preference(true)
                controller.bind(row, stream, false)
                assertNull(row.findViewById<View>(R.id.feed_preview_button))
                controller.bind(row, stream.copy(streamType = StreamType.LIVE_STREAM), true)
                assertNull(row.findViewById<View>(R.id.feed_preview_button))
                controller.bind(row, stream.copy(requiresMembership = true), true)
                assertNull(row.findViewById<View>(R.id.feed_preview_button))
                controller.bind(row, stream, true)
                assertNotNull(row.findViewById<View>(R.id.feed_preview_button))
                assertEquals(0, loads.get())
                row.findViewById<View>(R.id.feed_preview_button).performClick()
                assertNull(row.findViewById<View>(R.id.feed_preview_player))
            }
            assertEquals(0, loads.get())
        }
    }

    @Test
    fun switchingCardsAndDetachingCancelPendingExtraction() {
        val loads = AtomicInteger()
        val cancellations = AtomicInteger()
        withList({
            loads.incrementAndGet()
            Single.never<MediaSource>().doOnDispose { cancellations.incrementAndGet() }
        }) { scenario, list, controller ->
            scenario.onActivity { list.getChildAt(0).findViewById<View>(R.id.feed_preview_button).performClick() }
            await { loads.get() == 1 }
            scenario.onActivity { list.getChildAt(1).findViewById<View>(R.id.feed_preview_button).performClick() }
            await { loads.get() == 2 && cancellations.get() == 1 }
            scenario.onActivity {
                assertNull(list.getChildAt(0).findViewById<View>(R.id.feed_preview_player))
                assertNotNull(list.getChildAt(1).findViewById<View>(R.id.feed_preview_player))
                list.adapter = null
            }
            await { cancellations.get() == 2 }
            scenario.onActivity { controller.stop() }
            assertEquals(2, cancellations.get())
        }
    }

    @Test
    fun mutedPlaybackCanSeekAndHandOffWithoutKeepingPreviewAlive() {
        val context = instrumentation.targetContext
        val file = File(context.cacheDir, "feed-preview-test.mp4")
        file.writeBytes(Base64.decode(instrumentation.context.assets.open("video-adjustments/red-sdr.mp4.base64").use { it.readBytes() }, Base64.DEFAULT))
        try {
            withList({ Single.just(ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context)).createMediaSource(MediaItem.fromUri(file.toURI().toString()))) }) { scenario, list, controller ->
                scenario.onActivity {
                    val row = list.getChildAt(0)
                    row.findViewById<View>(R.id.itemDurationView).visibility = View.VISIBLE
                    row.findViewById<View>(R.id.feed_preview_button).performClick()
                    assertEquals(View.GONE, row.findViewById<View>(R.id.itemDurationView).visibility)
                }
                await {
                    var ready = false
                    scenario.onActivity { ready = list.getChildAt(0).findViewById<PlayerView>(R.id.feed_preview_player)?.player?.playbackState == Player.STATE_READY }
                    ready
                }
                scenario.onActivity {
                    val row = list.getChildAt(0)
                    val view = row.findViewById<PlayerView>(R.id.feed_preview_player)
                    val player = view.player!!
                    player.pause()
                    val actions = row.findViewById<View>(R.id.feed_preview_actions)
                    view.hideController()
                    assertEquals(View.GONE, actions.visibility)
                    view.showController()
                    assertEquals(View.VISIBLE, actions.visibility)
                    assertEquals(0f, player.volume, 0f)
                    row.findViewById<View>(R.id.feed_preview_mute).performClick()
                    assertEquals(1f, player.volume, 0f)
                    row.findViewById<View>(R.id.feed_preview_mute).performClick()
                    assertEquals(0f, player.volume, 0f)
                    player.seekTo(1500)
                    val queue = controller.takeQueue(stream)
                    assertEquals(1500L, requireNotNull(requireNotNull(queue).item).recoveryPosition)
                    assertEquals(View.VISIBLE, row.findViewById<View>(R.id.itemDurationView).visibility)
                    assertNull(view.player)
                    assertNull(row.findViewById<View>(R.id.feed_preview_player))
                    assertEquals(View.VISIBLE, row.findViewById<View>(R.id.feed_preview_button).visibility)
                    assertFalse(player.isPlaying)
                }
            }
        } finally {
            file.delete()
        }
    }

    private fun preference(enabled: Boolean) {
        val context = instrumentation.targetContext
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(context.getString(R.string.feed_preview_key), enabled).commit()
    }

    private fun withList(load: (StreamEntity) -> Single<MediaSource>, busy: Boolean = false, test: (ActivityScenario<AboutActivity>, RecyclerView, FeedPreviewController) -> Unit) {
        val context = instrumentation.targetContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val key = context.getString(R.string.feed_preview_key)
        val previous = prefs.getBoolean(key, false)
        preference(true)
        try {
            ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
                lateinit var list: RecyclerView
                lateinit var controller: FeedPreviewController
                scenario.onActivity { activity ->
                    list = RecyclerView(activity).apply { layoutManager = LinearLayoutManager(activity) }
                    controller = FeedPreviewController(list, { _, _ -> }, load, { busy })
                    list.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                        override fun getItemCount() = 2
                        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                            val row = LayoutInflater.from(parent.context).inflate(R.layout.list_stream_card_item, parent, false)
                            row.layoutParams.height = (180 * parent.resources.displayMetrics.density).toInt()
                            return object : RecyclerView.ViewHolder(row) {}
                        }
                        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = controller.bind(holder.itemView, stream.copy(url = if (position == 0) stream.url else "${stream.url}2"), true)
                    }
                    activity.setContentView(list)
                }
                try {
                    await {
                        var attached = false
                        scenario.onActivity { attached = list.childCount == 2 }
                        attached
                    }
                    test(scenario, list, controller)
                } finally {
                    scenario.onActivity { controller.dispose() }
                }
            }
        } finally {
            preference(previous)
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(25)
        assertTrue("Condition did not become true", condition())
    }
}
