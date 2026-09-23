package org.schabi.newpipe.local

import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.info_list.ItemViewMode
import org.schabi.newpipe.testUtil.TestDatabase
import org.schabi.newpipe.util.OnClickGesture

@RunWith(AndroidJUnit4::class)
class LocalItemListAdapterTest {
    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        database = TestDatabase.createReplacingNewPipeDatabase()
    }

    @After
    fun cleanUp() {
        database.close()
    }

    @Test
    fun exactPositionRemovalAndUndoPreserveDuplicateEntries() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val adapter = LocalItemListAdapter(context)
            val first = playlistEntry(joinIndex = 0)
            val duplicate = playlistEntry(joinIndex = 1)
            adapter.addItems(listOf(first, duplicate))
            adapter.setHeaderSupplier { View(context) }

            assertEquals(-1, adapter.getItemIndex(0))
            assertEquals(0, adapter.getItemIndex(1))
            assertEquals(1, adapter.getItemIndex(2))
            assertEquals(1, adapter.getAdapterPositionForItemIndex(0))
            assertEquals(2, adapter.getAdapterPositionForItemIndex(1))
            assertEquals(RecyclerView.NO_POSITION, adapter.getAdapterPositionForItemIndex(2))

            assertSame(duplicate, adapter.removeItemAt(1))
            assertEquals(listOf(first), adapter.itemsList)

            adapter.insertItemAt(1, duplicate)
            assertEquals(listOf(first, duplicate), adapter.itemsList)
        }
    }

    @Test
    fun bookmarksFollowViewModeAndKeepItWhenDragHandlesAreDisabled() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.LightTheme)
            val adapter = LocalItemListAdapter(context)
            adapter.setUseItemHandle(true)
            adapter.addItems(
                listOf(
                    PlaylistMetadataEntry(1, "Wisso", null, 0, false, null, 4),
                    PlaylistRemoteEntity(
                        serviceId = 0,
                        orderingName = "Remote playlist",
                        url = "https://example.com/playlist",
                        thumbnailUrl = null,
                        uploader = "Uploader",
                        streamCount = 4
                    )
                )
            )
            var selected: LocalItem? = null
            var dragCount = 0
            adapter.setSelectedListener(object : OnClickGesture<LocalItem> {
                override fun selected(selectedItem: LocalItem) {
                    selected = selectedItem
                }

                override fun drag(selectedItem: LocalItem, viewHolder: RecyclerView.ViewHolder) {
                    dragCount++
                }
            })
            val viewTypes = mutableSetOf<Int>()
            for (mode in listOf(ItemViewMode.LIST, ItemViewMode.CARD, ItemViewMode.GRID)) {
                adapter.setItemViewMode(mode)
                for (position in 0..1) {
                    val type = adapter.getItemViewType(position)
                    viewTypes.add(type)
                    val holder = adapter.createViewHolder(FrameLayout(context), type)
                    val handle = holder.itemView.findViewById<View>(R.id.itemHandle)
                    val thumbnail = holder.itemView.findViewById<View>(R.id.itemThumbnailContainer)
                    val image = holder.itemView.findViewById<android.widget.ImageView>(
                        R.id.itemThumbnailView
                    )
                    val title = holder.itemView.findViewById<TextView>(R.id.itemTitleView)
                    var thumbnailWidth = 0
                    var thumbnailHeight = 0
                    for (enabled in listOf(true, false, true)) {
                        adapter.setItemHandleEnabled(enabled)
                        assertEquals(type, adapter.getItemViewType(position))
                        adapter.bindViewHolder(holder, position)
                        val widthDp = if (mode == ItemViewMode.GRID) 180 else 400
                        val width = (widthDp * context.resources.displayMetrics.density).toInt()
                        holder.itemView.measure(
                            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                        )
                        holder.itemView.layout(0, 0, width, holder.itemView.measuredHeight)
                        assertTrue(thumbnail.width > 0)
                        assertTrue(thumbnail.height > 0)
                        if (mode == ItemViewMode.CARD) {
                            assertEquals(
                                android.widget.ImageView.ScaleType.FIT_START,
                                image.scaleType
                            )
                        } else if (mode == ItemViewMode.GRID) {
                            assertEquals(
                                android.widget.ImageView.ScaleType.FIT_CENTER,
                                image.scaleType
                            )
                        }
                        if (mode == ItemViewMode.LIST) {
                            assertTrue(title.left >= thumbnail.right)
                            assertTrue(title.top < thumbnail.bottom)
                        } else {
                            assertTrue(title.top >= thumbnail.bottom)
                            assertEquals(width - holder.itemView.paddingLeft - holder.itemView.paddingRight, thumbnail.width)
                            assertEquals(16.0 / 9.0, thumbnail.width.toDouble() / thumbnail.height, 0.05)
                        }
                        if (thumbnailWidth != 0) {
                            assertEquals(thumbnailWidth, thumbnail.width)
                            assertEquals(thumbnailHeight, thumbnail.height)
                        }
                        thumbnailWidth = thumbnail.width
                        thumbnailHeight = thumbnail.height
                        assertEquals(if (enabled) View.VISIBLE else View.GONE, handle.visibility)
                        assertEquals(enabled, handle.isEnabled)
                        assertTrue(title.text.isNotEmpty())
                        holder.itemView.performClick()
                        assertSame(adapter.itemsList[position], selected)
                        val previousDragCount = dragCount
                        val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 1f, 1f, 0)
                        try {
                            handle.dispatchTouchEvent(event)
                        } finally {
                            event.recycle()
                        }
                        assertEquals(previousDragCount + if (enabled) 1 else 0, dragCount)
                    }
                }
            }
            assertEquals(6, viewTypes.size)
        }
    }

    @Test
    fun ordinaryPlaylistCardsDoNotExposeBookmarkDragHandles() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.LightTheme)
            val adapter = LocalItemListAdapter(context)
            adapter.addItems(listOf(PlaylistMetadataEntry(1, "Playlist", null, 0, false, null, 4)))
            for (mode in listOf(ItemViewMode.CARD, ItemViewMode.GRID)) {
                adapter.setItemViewMode(mode)
                val holder = adapter.createViewHolder(FrameLayout(context), adapter.getItemViewType(0))
                adapter.bindViewHolder(holder, 0)
                val handle = holder.itemView.findViewById<View>(R.id.itemHandle)
                assertEquals(View.GONE, handle.visibility)
                assertFalse(handle.hasOnClickListeners())
            }
        }
    }

    private fun playlistEntry(joinIndex: Int) = PlaylistStreamEntry(
        streamEntity = StreamEntity(
            serviceId = 0,
            url = "https://example.com/video",
            title = "Video",
            streamType = StreamType.VIDEO_STREAM,
            duration = 60,
            uploader = "Uploader",
            uploaderUrl = "https://example.com/channel"
        ),
        progressMillis = 0,
        streamId = 1,
        joinIndex = joinIndex
    )
}
