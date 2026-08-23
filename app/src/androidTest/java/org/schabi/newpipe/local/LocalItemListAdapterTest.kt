package org.schabi.newpipe.local

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.testUtil.TestDatabase

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
