package org.schabi.newpipe.local.holder

import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.util.OnClickGesture
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.local.LocalItemBuilder

@RunWith(AndroidJUnit4::class)
class RemotePlaylistItemHolderTest {
    @Test
    fun unknownCountIsHiddenAndKnownCountRestoresBadge() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.LightTheme)
            val holder = RemotePlaylistItemHolder(
                LocalItemBuilder(context),
                FrameLayout(context)
            )

            holder.updateFromItem(
                remotePlaylist(streamCount = null),
                null,
                DateTimeFormatter.ISO_LOCAL_DATE
            )

            assertEquals(View.GONE, holder.itemStreamCountView.visibility)
            assertTrue(holder.itemStreamCountView.text.isEmpty())

            holder.updateFromItem(
                remotePlaylist(streamCount = 42),
                null,
                DateTimeFormatter.ISO_LOCAL_DATE
            )

            assertEquals(View.VISIBLE, holder.itemStreamCountView.visibility)
            assertTrue(holder.itemStreamCountView.text.isNotEmpty())
        }
    }

    @Test
    fun gridChildLongPressDispatchesHeldAction() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.LightTheme)
            var heldItem: LocalItem? = null
            val builder = LocalItemBuilder(context)
            builder.setOnItemSelectedListener(object : OnClickGesture<LocalItem>() {
                override fun selected(selectedItem: LocalItem) = Unit

                override fun held(selectedItem: LocalItem) {
                    heldItem = selectedItem
                }
            })
            val holder = RemoteBookmarkPlaylistItemHolder(
                builder,
                R.layout.list_playlist_grid_item,
                FrameLayout(context)
            )
            val playlist = remotePlaylist(streamCount = 42)
            holder.updateFromItem(playlist, null, DateTimeFormatter.ISO_LOCAL_DATE)

            assertTrue(holder.itemView.findViewById<View>(R.id.itemThumbnailContainer)
                .performLongClick())
            assertEquals(playlist, heldItem)
        }
    }

    private fun remotePlaylist(streamCount: Long?) = PlaylistRemoteEntity(
        serviceId = 0,
        orderingName = "Synchronized playlist",
        url = "https://www.youtube.com/playlist?list=test",
        thumbnailUrl = null,
        uploader = "Uploader",
        streamCount = streamCount
    )
}
