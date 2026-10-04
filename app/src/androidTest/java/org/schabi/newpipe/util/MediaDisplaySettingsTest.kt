/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.util

import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding
import org.schabi.newpipe.views.MediaTextView

class MediaDisplaySettingsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    @Test
    fun metadataSizeUpdatesViewsDateAndDurationWithoutChangingTitle() {
        val key = context.getString(R.string.video_metadata_text_size_key)
        val old = prefs.getString(key, null)
        prefs.edit().putString(key, "100").commit()
        try {
            ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val host = LinearLayout(activity)
                    activity.setContentView(host)
                    val row = activity.layoutInflater.inflate(R.layout.list_stream_item, host, false)
                    host.addView(row)
                    val details = row.findViewById<TextView>(R.id.itemAdditionalDetails)
                    val duration = row.findViewById<TextView>(R.id.itemDurationView)
                    val title = row.findViewById<TextView>(R.id.itemVideoTitleView)
                    val detailsSize = details.textSize
                    val durationSize = duration.textSize
                    val titleSize = title.textSize
                    prefs.edit().putString(key, "150").commit()
                    assertTrue(details.textSize > detailsSize)
                    assertTrue(duration.textSize > durationSize)
                    assertEquals(titleSize, title.textSize, 0.01f)
                    val enlarged = details.textSize
                    host.removeView(row)
                    host.addView(row)
                    assertEquals(enlarged, details.textSize, 0.01f)
                    prefs.edit().putString(key, "100").commit()
                    assertEquals(detailsSize, details.textSize, 0.01f)
                    assertEquals(durationSize, duration.textSize, 0.01f)
                }
            }
        } finally {
            prefs.edit().apply {
                if (old == null) remove(key) else putString(key, old)
            }.commit()
        }
    }

    @Test
    fun titleAndChannelSizesUpdateIndependentlyAndResetWithoutCompounding() = withSettings {
        val titleKey = context.getString(R.string.video_title_text_size_key)
        val channelKey = context.getString(R.string.channel_name_text_size_key)
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = LinearLayout(activity)
                activity.setContentView(host)
                val row = activity.layoutInflater.inflate(R.layout.list_stream_card_item, host, false)
                host.addView(row)
                val title = row.findViewById<MediaTextView>(R.id.itemVideoTitleView)
                val channel = row.findViewById<TextView>(R.id.itemUploaderView)
                val titleSize = title.textSize
                val channelSize = channel.textSize
                prefs.edit().putString(titleKey, "150").commit()
                assertTrue(title.textSize > titleSize)
                assertEquals(channelSize, channel.textSize, 0.01f)
                val largeSize = title.textSize
                prefs.edit().putString(channelKey, "80").commit()
                assertTrue(channel.textSize < channelSize)
                assertEquals(largeSize, title.textSize, 0.01f)
                // Bottom player controls can enlarge metadata independently of the preference.
                title.setPresentationScale(1.2f)
                assertTrue(title.textSize > largeSize)
                title.setPresentationScale(1.0f)
                assertEquals(largeSize, title.textSize, 0.01f)
                host.removeView(row)
                prefs.edit().putString(titleKey, "100").putString(channelKey, "100").commit()
                host.addView(row)
                assertEquals(titleSize, title.textSize, 0.01f)
                assertEquals(channelSize, channel.textSize, 0.01f)
                prefs.edit().putString(titleKey, "150").commit()
                assertEquals(largeSize, title.textSize, 0.01f)
                prefs.edit().putString(titleKey, "invalid").commit()
                assertEquals(titleSize, title.textSize, 0.01f)
            }
        }
    }

    @Test
    fun listGridPlaylistAndPlayerLayoutsApplyTheChosenSizesAtInflation() = withSettings {
        instrumentation.runOnMainSync {
            val themed = ContextThemeWrapper(context, R.style.LightTheme)
            val layouts = listOf(R.layout.list_stream_item, R.layout.list_stream_grid_item, R.layout.list_stream_card_item, R.layout.list_stream_related_wide_item, R.layout.list_stream_playlist_item)
            for (layout in layouts) {
                prefs.edit().putString(context.getString(R.string.video_title_text_size_key), "100").putString(context.getString(R.string.channel_name_text_size_key), "100").commit()
                val original = LayoutInflater.from(themed).inflate(layout, null)
                prefs.edit().putString(context.getString(R.string.video_title_text_size_key), "150").putString(context.getString(R.string.channel_name_text_size_key), "80").commit()
                val changed = LayoutInflater.from(themed).inflate(layout, null)
                assertTrue(changed.findViewById<TextView>(R.id.itemVideoTitleView).textSize > original.findViewById<TextView>(R.id.itemVideoTitleView).textSize)
                assertTrue(changed.findViewById<TextView>(R.id.itemUploaderView).textSize < original.findViewById<TextView>(R.id.itemUploaderView).textSize)
            }
            val player = LayoutInflater.from(themed).inflate(R.layout.player, null)
            assertTrue(player.findViewById<View>(R.id.titleTextView) is MediaTextView)
            assertTrue(player.findViewById<View>(R.id.channelTextView) is MediaTextView)
        }
    }

    @Test
    fun tvSidebarGivesSpaceBackToThePlayerAndCanRestoreItsOldWidth() = withSettings {
        instrumentation.runOnMainSync {
            val tvField = DeviceUtils::class.java.getDeclaredField("isTV").apply { isAccessible = true }
            val oldTv = tvField.get(null)
            try {
                val configuration = Configuration(context.resources.configuration).apply {
                    orientation = Configuration.ORIENTATION_LANDSCAPE
                    screenWidthDp = 960
                    screenHeightDp = 540
                }
                val themed = ContextThemeWrapper(context.createConfigurationContext(configuration), R.style.LightTheme)
                val binding = FragmentVideoDetailBinding.inflate(LayoutInflater.from(themed))
                val sidebar = requireNotNull(binding.relatedItemsLayout)
                val key = context.getString(R.string.tv_related_sidebar_width_key)
                tvField.set(null, true)
                prefs.edit().putString(key, "compact").commit()
                TvRelatedSidebar.apply(binding.detailMainContent, sidebar)
                measure(binding.root)
                assertEquals(250.0, sidebar.width.toDouble(), 1.0)
                assertEquals(750.0, binding.detailMainContent.width.toDouble(), 1.0)
                prefs.edit().putString(key, "wide").commit()
                TvRelatedSidebar.apply(binding.detailMainContent, sidebar)
                measure(binding.root)
                assertEquals(444.0, sidebar.width.toDouble(), 1.0)
                sidebar.visibility = View.GONE
                measure(binding.root)
                assertEquals(1000, binding.detailMainContent.width)
                sidebar.visibility = View.VISIBLE
                prefs.edit().putString(key, "compact").commit()
                tvField.set(null, false)
                TvRelatedSidebar.apply(binding.detailMainContent, sidebar)
                measure(binding.root)
                assertEquals(444.0, sidebar.width.toDouble(), 1.0)
            } finally {
                tvField.set(null, oldTv)
            }
        }
    }

    private fun measure(view: View) {
        view.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1000, 600)
    }

    private fun withSettings(test: () -> Unit) {
        val keys = listOf(R.string.video_title_text_size_key, R.string.channel_name_text_size_key, R.string.tv_related_sidebar_width_key).map(context::getString)
        val previous = keys.associateWith { prefs.getString(it, null) }
        prefs.edit().apply { keys.forEach(::remove) }.commit()
        try {
            test()
        } finally {
            prefs.edit().apply { previous.forEach { (key, value) -> putString(key, value) } }.commit()
        }
    }
}
