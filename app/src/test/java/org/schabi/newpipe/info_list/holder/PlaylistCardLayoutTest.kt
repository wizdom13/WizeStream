package org.schabi.newpipe.info_list.holder

import android.app.Application
import android.content.pm.ApplicationInfo
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.schabi.newpipe.R
import org.schabi.newpipe.info_list.InfoItemBuilder
import org.schabi.newpipe.util.GridLayoutManagerHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaylistCardLayoutTest {
    @Test
    fun desktopAndResizedLandscapeWindowsKeepCompactPlaylistAndAlbumCards() {
        RuntimeEnvironment.setQualifiers("w1280dp-h720dp-land-mdpi")
        val parent = parent()
        val card = card(parent)
        for (width in listOf(1864, 1280, 800)) {
            layout(card, width)
            val thumbnail = card.findViewById<View>(R.id.itemThumbnailContainer)
            val title = card.findViewById<View>(R.id.itemTitleView)
            val uploader = card.findViewById<View>(R.id.itemUploaderView)
            assertEquals(parent.resources.getDimensionPixelSize(R.dimen.video_item_search_thumbnail_image_width), thumbnail.width)
            assertTrue(card.height < 160)
            assertTrue(title.left >= thumbnail.right)
            assertTrue(title.width > 0)
            assertTrue(uploader.bottom <= card.height)
        }
    }

    @Test
    fun portraitKeepsFullWidthCardsAndLandscapeSupportsRtl() {
        RuntimeEnvironment.setQualifiers("w360dp-h800dp-port-mdpi")
        val portrait = card(parent())
        layout(portrait, 360)
        val thumbnail = portrait.findViewById<View>(R.id.itemThumbnailContainer)
        assertTrue(thumbnail.width > 300)
        assertTrue(thumbnail.height > 160)

        RuntimeEnvironment.setQualifiers("ar-ldrtl-w1280dp-h720dp-land-mdpi")
        val rtl = card(parent()).apply { layoutDirection = View.LAYOUT_DIRECTION_RTL }
        layout(rtl, 1280)
        val rtlThumbnail = rtl.findViewById<View>(R.id.itemThumbnailContainer)
        val title = rtl.findViewById<View>(R.id.itemTitleView)
        assertTrue(title.right <= rtlThumbnail.left)
        assertTrue(title.width > 0)
        assertTrue(rtl.height < 160)
    }

    @Test
    fun rotationRecreatesAttachedAndCachedCardsWithTheNewLayout() {
        RuntimeEnvironment.setQualifiers("w360dp-h800dp-port-mdpi")
        val context = parent().context
        val list = RecyclerView(context)
        val adapter = object : RecyclerView.Adapter<PlaylistCardInfoItemHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = PlaylistCardInfoItemHolder(InfoItemBuilder(parent.context), parent)
            override fun getItemCount() = 12
            override fun onBindViewHolder(holder: PlaylistCardInfoItemHolder, position: Int) {
                holder.itemTitleView.text = "Playlist $position"
                holder.itemUploaderView.text = "Channel"
            }
        }
        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter
        list.itemAnimator = null
        layout(list, 360)
        assertTrue(list.getChildAt(0).height > 180)

        RuntimeEnvironment.setQualifiers("w1280dp-h720dp-land-mdpi")
        GridLayoutManagerHelper.preserveScrollPosition(list) {
            GridLayoutManagerHelper.reinflateViewHolders(list)
            list.layoutManager = LinearLayoutManager(context)
        }
        layout(list, 1280)
        assertTrue(list.childCount > 1)
        for (index in 0 until list.childCount) assertTrue(list.getChildAt(index).height < 160)
        assertEquals(adapter, list.adapter)

        RuntimeEnvironment.setQualifiers("w360dp-h800dp-port-mdpi")
        GridLayoutManagerHelper.reinflateViewHolders(list)
        layout(list, 360)
        assertTrue(list.getChildAt(0).height > 180)
    }

    private fun parent(): FrameLayout {
        val context = RuntimeEnvironment.getApplication()
        // Enable RTL in the test application so the relative constraints are exercised.
        context.applicationInfo.flags = context.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        context.setTheme(R.style.LightTheme)
        return FrameLayout(context)
    }

    private fun card(parent: ViewGroup): View = PlaylistCardInfoItemHolder(InfoItemBuilder(parent.context), parent).itemView.apply {
        findViewById<TextView>(R.id.itemTitleView).text = "Playlist or album title"
        findViewById<TextView>(R.id.itemUploaderView).text = "Channel"
        findViewById<TextView>(R.id.itemStreamCountView).text = "42"
    }

    private fun layout(view: View, width: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST))
        view.layout(0, 0, width, view.measuredHeight)
    }
}
