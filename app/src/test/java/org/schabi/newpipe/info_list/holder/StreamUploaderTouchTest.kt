/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.info_list.holder

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Looper
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.database.stream.StreamWithState
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.databinding.ListStreamItemBinding
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.info_list.InfoItemBuilder
import org.schabi.newpipe.local.LocalItemBuilder
import org.schabi.newpipe.local.feed.item.StreamItem
import org.schabi.newpipe.local.holder.LocalStatisticStreamItemHolder
import org.schabi.newpipe.util.OnClickGesture
import org.schabi.newpipe.util.image.ImageStrategy
import org.schabi.newpipe.util.image.PreferredImageQuality

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w360dp-h800dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StreamUploaderTouchTest {
    @Before
    fun disableNetworkImages() {
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.NONE)
    }

    @After
    fun restoreImagePreference() {
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.MEDIUM)
    }

    @Test
    fun actualRecyclerViewTapsChooseExactlyOneActionInEveryLayoutAndDirection() {
        for (case in cases()) {
            fixture(case).use { row ->
                val target = row.bounds(R.id.itemUploaderLink)
                assertTrue(case.toString(), target.width() >= 48 && target.height() >= 48)
                for (id in listOf(R.id.itemUploaderAvatarView, R.id.itemUploaderView)) {
                    row.tap(row.bounds(id))
                    assertEquals(case.toString(), listOf("channel:channel"), row.takeActions())
                }
                // The extra vertical padding is part of the channel action, not the video row.
                row.tap(target.centerX().toFloat(), target.top + 2f)
                assertEquals(case.toString(), listOf("channel:channel"), row.takeActions())
                for (id in listOf(R.id.itemVideoTitleView, R.id.itemThumbnailContainer, R.id.itemAdditionalDetails)) {
                    row.tap(row.bounds(id))
                    assertEquals(case.toString(), listOf("video:video"), row.takeActions())
                }
                val metadata = row.bounds(R.id.itemUploaderRoot)
                val blankX = if (case.rtl) metadata.left + 1f else metadata.right - 1f
                assertFalse("The unrelated end of a short-name row is outside the channel target", target.contains(blankX.toInt(), target.centerY()))
                row.tap(blankX, target.centerY().toFloat())
                assertEquals(case.toString(), listOf("video:video"), row.takeActions())
            }
        }
    }

    @Test
    fun shortAndMissingNamesAvatarsAndUrlsResetRecycledActionsAndAccessibility() {
        fixture(Case(R.layout.list_stream_item)).use { row ->
            row.bind(stream(name = "A", avatar = null))
            assertEquals(View.GONE, row.view(R.id.itemUploaderAvatarView).visibility)
            val target = row.view(R.id.itemUploaderLink)
            val minimum = target.resources.getDimensionPixelSize(R.dimen.stream_item_uploader_touch_target)
            assertTrue(target.width >= minimum && target.height >= minimum)
            assertTrue(target.isEnabled && target.isClickable && target.isFocusable)
            assertEquals(target.context.getString(R.string.open_channel, "A"), target.contentDescription)
            assertFalse(row.view(R.id.itemUploaderView).isClickable)
            assertFalse(row.view(R.id.itemUploaderAvatarView).isClickable)
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, row.view(R.id.itemUploaderView).importantForAccessibility)
            row.tap(row.bounds(R.id.itemUploaderLink))
            assertEquals(listOf("channel:channel"), row.takeActions())
            assertTrue(target.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
            assertEquals(listOf("channel:channel"), row.takeActions())

            for (avatar in listOf(null, "https://example.invalid/avatar")) {
                row.bind(stream(name = " ", avatar = avatar, channel = "unnamed"))
                assertEquals(target.context.getString(R.string.show_channel_details), target.contentDescription)
                assertTrue((row.view(R.id.itemUploaderView) as TextView).text.isNotBlank())
                row.tap(row.bounds(R.id.itemUploaderLink))
                assertEquals(listOf("channel:unnamed"), row.takeActions())
            }

            row.bind(stream(channel = "", avatar = null))
            assertFalse(target.isEnabled || target.isClickable || target.isFocusable)
            assertNull(target.contentDescription)
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, target.importantForAccessibility)
            row.tap(row.bounds(R.id.itemUploaderView))
            assertEquals(listOf("video:video"), row.takeActions())

            row.bind(stream(channel = "next"))
            assertEquals(View.VISIBLE, row.view(R.id.itemUploaderAvatarView).visibility)
            row.tap(row.bounds(R.id.itemUploaderLink))
            assertEquals(listOf("channel:next"), row.takeActions())
            row.builder.onChannelSelectedListener = null
            row.bind(stream())
            row.tap(row.bounds(R.id.itemUploaderView))
            assertEquals(listOf("video:video"), row.takeActions())
            assertFalse(target.isClickable)
        }
    }

    @Test
    fun cancellationScrollingAndRebindingDuringAPressDoNotNavigate() {
        fixture(Case(R.layout.list_stream_item)).use { row ->
            val target = row.bounds(R.id.itemUploaderLink)
            row.touch(MotionEvent.ACTION_DOWN, target.centerX().toFloat(), target.centerY().toFloat())
            row.touch(MotionEvent.ACTION_CANCEL, target.centerX().toFloat(), target.centerY().toFloat())
            row.drain()
            assertTrue(row.takeActions().isEmpty())

            row.touch(MotionEvent.ACTION_DOWN, target.centerX().toFloat(), target.centerY().toFloat())
            row.bind(stream(channel = "replacement"))
            row.touch(MotionEvent.ACTION_UP, target.centerX().toFloat(), target.centerY().toFloat())
            row.drain()
            assertTrue("A rebind cancels the old pending gesture", row.takeActions().isEmpty())

            row.touch(MotionEvent.ACTION_DOWN, target.centerX().toFloat(), target.centerY().toFloat())
            row.touch(MotionEvent.ACTION_MOVE, target.centerX().toFloat(), target.centerY() - 30f)
            row.touch(MotionEvent.ACTION_MOVE, target.centerX().toFloat(), target.centerY() - 70f)
            row.touch(MotionEvent.ACTION_UP, target.centerX().toFloat(), target.centerY() - 70f)
            row.drain()
            assertTrue("A channel-started swipe must still scroll the RecyclerView", row.list.computeVerticalScrollOffset() > 0)
            assertTrue(row.takeActions().isEmpty())
        }
    }

    @Test
    fun narrowRowsAndLargeFontsKeepTargetsTitlesThumbnailsAndMetadataContained() {
        for (case in cases().map { it.copy(width = if (it.layout == R.layout.list_stream_grid_item) 180 else 280, fontScale = 1.8f) }) {
            fixture(case).use { row ->
                row.bind(stream(name = "A long channel name ".repeat(12)))
                val root = row.bounds(row.holder.itemView)
                val metadata = row.bounds(R.id.itemUploaderRoot)
                val target = row.bounds(R.id.itemUploaderLink)
                val details = row.bounds(R.id.itemAdditionalDetails)
                assertTrue(case.toString(), target.width() >= 48 && target.height() >= 48)
                assertTrue("$case: target inside metadata row", metadata.contains(target))
                assertTrue("$case: details inside metadata row", metadata.contains(details))
                assertFalse("$case: metadata must remain outside channel hit area", Rect.intersects(target, details))
                for (id in listOf(R.id.itemVideoTitleView, R.id.itemThumbnailContainer, R.id.itemUploaderView, R.id.itemUploaderAvatarView)) {
                    val bounds = row.bounds(id)
                    assertTrue("$case: ${row.view(id).resources.getResourceEntryName(id)} is visible", bounds.width() > 0 && bounds.height() > 0)
                    assertTrue("$case: $bounds within $root", root.contains(bounds))
                }
                assertFalse("$case: title cannot overlap the channel target", Rect.intersects(row.bounds(R.id.itemVideoTitleView), target))
                row.tap(target)
                assertEquals(listOf("channel:channel"), row.takeActions())
                row.tap(details)
                assertEquals(listOf("video:video"), row.takeActions())
                row.bind(stream(name = "A", avatar = null))
                assertTrue("$case: rebind restores a bounded short-name target", row.view(R.id.itemUploaderLink).width < metadata.width())
            }
        }
    }

    @Test
    fun sharedFeedBindingUsesTheSameBoundedTargetAndClearsMissingUrls() {
        fixture(Case(R.layout.list_stream_card_item)).use { row ->
            val entity = StreamEntity(stream()).apply { uploaderAvatarUrl = "https://example.invalid/avatar" }
            val feed = StreamItem(StreamWithState(entity, null), StreamItem.ItemVersion.CARD).apply {
                onUploaderSelected = { row.actions += "feed:${it.url.substringAfterLast('/')}" }
            }
            fun bind() {
                feed.bind(ListStreamItemBinding.bind(row.holder.itemView), 0)
                row.layout()
            }
            bind()
            row.tap(row.bounds(R.id.itemUploaderLink))
            assertEquals(listOf("feed:channel"), row.takeActions())
            row.tap(row.bounds(R.id.itemAdditionalDetails))
            assertEquals(listOf("video:video"), row.takeActions())
            entity.uploaderUrl = null
            bind()
            row.tap(row.bounds(R.id.itemUploaderLink))
            assertEquals(listOf("video:video"), row.takeActions())
        }
    }

    @Test
    fun sharedHistoryLayoutsKeepSparseUploaderLookupAndSelectionModeWithoutCapturingMetadata() {
        fixture(Case(R.layout.list_stream_item)).use { row ->
            val entity = StreamEntity(stream()).apply { uploaderUrl = null }
            val entry = StreamStatisticsEntry(entity, 0, 1, OffsetDateTime.parse("2026-11-28T12:00:00Z"), 1)
            val builder = LocalItemBuilder(row.list.context).apply {
                setOnItemSelectedListener { row.actions += "history-video" }
                setOnUploaderSelectedListener { row.actions += "history-channel" }
            }
            row.list.adapter = object : RecyclerView.Adapter<LocalStatisticStreamItemHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = LocalStatisticStreamItemHolder(builder, parent)
                override fun getItemCount() = 20
                override fun onBindViewHolder(holder: LocalStatisticStreamItemHolder, position: Int) = holder.updateFromItem(entry, null, DateTimeFormatter.ISO_LOCAL_DATE)
            }
            row.layout()
            row.tap(row.bounds(R.id.itemUploaderLink))
            assertEquals(listOf("history-channel"), row.takeActions())
            row.tap(row.bounds(R.id.itemAdditionalDetails))
            assertEquals(listOf("history-video"), row.takeActions())
            assertEquals(row.bounds(R.id.itemUploaderView).left, row.bounds(R.id.itemAdditionalDetails).left)
            builder.setSelectionToggle { row.actions += "select" }
            row.tap(row.bounds(R.id.itemUploaderLink))
            assertEquals(listOf("select"), row.takeActions())

            builder.setSelectionToggle(null)
            entity.sourceType = StreamEntity.SOURCE_TYPE_LOCAL
            (row.holder as LocalStatisticStreamItemHolder).updateFromItem(entry, null, DateTimeFormatter.ISO_LOCAL_DATE)
            row.layout()
            assertFalse(row.view(R.id.itemUploaderLink).isEnabled)
            row.tap(row.bounds(R.id.itemUploaderLink))
            assertEquals(listOf("history-video"), row.takeActions())
        }
    }

    @Test
    fun compactMiniRowsRetainTheirVideoAction() {
        fixture(Case(R.layout.list_stream_mini_item)).use { row ->
            assertNull(row.holder.itemView.findViewById<View>(R.id.itemUploaderLink))
            row.tap(row.bounds(R.id.itemUploaderView))
            assertEquals(listOf("video:video"), row.takeActions())
        }
    }

    private fun cases() = listOf(
        Case(R.layout.list_stream_item),
        Case(R.layout.list_stream_grid_item, width = 180),
        Case(R.layout.list_stream_card_item),
        Case(R.layout.list_stream_card_item, width = 640, landscape = true),
        Case(R.layout.list_stream_related_wide_item)
    ).flatMap { listOf(it, it.copy(rtl = true)) }

    private fun stream(name: String = "Channel", avatar: String? = "https://example.invalid/avatar", channel: String = "channel") = StreamInfoItem(
        0,
        "https://example.invalid/video",
        "A sample video",
        StreamType.VIDEO_STREAM
    ).apply {
        uploaderName = name
        uploaderAvatarUrl = avatar
        uploaderUrl = if (channel.isEmpty()) "" else "https://example.invalid/$channel"
        viewCount = 12000
        textualUploadDate = "2 years ago"
    }

    private fun fixture(case: Case): Row {
        RuntimeEnvironment.setQualifiers("${if (case.rtl) "ar-ldrtl-" else "en-ldltr-"}w${case.width}dp-h800dp-${if (case.landscape) "land" else "port"}-mdpi")
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        val activity = controller.get().apply {
            applicationInfo.flags = applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
            setTheme(R.style.LightTheme)
        }
        controller.setup().visible()
        val config = Configuration(activity.resources.configuration).apply { fontScale = case.fontScale }
        val context = ContextThemeWrapper(activity.createConfigurationContext(config), R.style.LightTheme)
        val list = RecyclerView(context).apply {
            layoutDirection = if (case.rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            layoutManager = LinearLayoutManager(context)
            itemAnimator = null
        }
        val actions = mutableListOf<String>()
        val builder = InfoItemBuilder(context).apply {
            onStreamSelectedListener = OnClickGesture { actions += "video:${it.url.substringAfterLast('/')}" }
            onChannelSelectedListener = OnClickGesture { actions += "channel:${it.url.substringAfterLast('/')}" }
        }
        list.adapter = object : RecyclerView.Adapter<StreamMiniInfoItemHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = if (case.layout == R.layout.list_stream_mini_item) {
                StreamMiniInfoItemHolder(builder, parent)
            } else {
                StreamInfoItemHolder(builder, case.layout, parent)
            }
            override fun getItemCount() = 20
            override fun onBindViewHolder(holder: StreamMiniInfoItemHolder, position: Int) = holder.updateFromItem(stream(), null)
            override fun onViewRecycled(holder: StreamMiniInfoItemHolder) = holder.recycle()
        }
        activity.setContentView(list)
        return Row(controller, list, builder, actions, case.width).also { it.layout() }
    }

    private data class Case(val layout: Int, val width: Int = 360, val landscape: Boolean = false, val rtl: Boolean = false, val fontScale: Float = 1f)

    private class Row(
        private val controller: ActivityController<AppCompatActivity>,
        val list: RecyclerView,
        val builder: InfoItemBuilder,
        val actions: MutableList<String>,
        private val width: Int
    ) : AutoCloseable {
        private var downTime = 0L
        val holder: RecyclerView.ViewHolder get() = list.getChildViewHolder(list.getChildAt(0))
        fun view(id: Int): View = holder.itemView.findViewById(id)
        fun bounds(id: Int): Rect = bounds(view(id))
        fun bounds(view: View) = Rect(0, 0, view.width, view.height).apply { list.offsetDescendantRectToMyCoords(view, this) }
        fun bind(item: StreamInfoItem) {
            (holder as StreamMiniInfoItemHolder).updateFromItem(item, null)
            layout()
        }
        fun layout() {
            list.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
            list.layout(0, 0, width, 720)
            drain()
        }
        fun tap(rect: Rect) = tap(rect.centerX().toFloat(), rect.centerY().toFloat())
        fun tap(x: Float, y: Float) {
            touch(MotionEvent.ACTION_DOWN, x, y)
            touch(MotionEvent.ACTION_UP, x, y)
            drain()
        }
        fun touch(action: Int, x: Float, y: Float) {
            if (action == MotionEvent.ACTION_DOWN) downTime = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            try {
                list.dispatchTouchEvent(event)
            } finally {
                event.recycle()
            }
            shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
        }
        fun drain() {
            shadowOf(Looper.getMainLooper()).idle()
        }
        fun takeActions(): List<String> = actions.toList().also { actions.clear() }
        override fun close() {
            list.stopScroll()
            list.adapter = null
            controller.pause().stop().destroy()
            drain()
        }
    }
}
