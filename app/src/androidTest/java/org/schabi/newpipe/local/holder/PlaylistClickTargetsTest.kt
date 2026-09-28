package org.schabi.newpipe.local.holder

import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.local.LocalItemBuilder
import org.schabi.newpipe.util.OnClickGesture

@RunWith(AndroidJUnit4::class)
class PlaylistClickTargetsTest {
    @Test
    fun cardThumbnailAndTitleOpenPlaylist() {
        assertCardTargetDispatchesClick(R.id.itemThumbnailContainer)
        assertCardTargetDispatchesClick(R.id.itemThumbnailView)
        assertCardTargetDispatchesClick(R.id.itemTitleView)
    }

    private fun assertCardTargetDispatchesClick(targetId: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.LightTheme)
            var selected: LocalItem? = null
            val builder = LocalItemBuilder(context)
            builder.setOnItemSelectedListener(object : OnClickGesture<LocalItem> {
                override fun selected(selectedItem: LocalItem) {
                    selected = selectedItem
                }
            })
            val holder = RemotePlaylistCardItemHolder(builder, FrameLayout(context))
            val playlist = remotePlaylist()
            holder.updateFromItem(playlist, null, DateTimeFormatter.ISO_LOCAL_DATE)

            assertTrue(holder.itemView.findViewById<View>(targetId).performClick())
            assertEquals(playlist, selected)
        }
    }

    private fun remotePlaylist() = PlaylistRemoteEntity(
        serviceId = 0,
        orderingName = "Playlist",
        url = "https://www.youtube.com/playlist?list=test",
        thumbnailUrl = null,
        uploader = "Uploader",
        streamCount = 1
    )
}
