package org.schabi.newpipe.util

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.R as MaterialR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R

@RunWith(AndroidJUnit4::class)
class MainContentThemeTest {
    @Test
    fun transparentPagesHaveAReadableSurfaceInForcedLightDarkAndBlackModes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES).forEach { systemMode ->
                val configuration = Configuration(context.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or systemMode
                }
                listOf(R.style.LightTheme, R.style.DarkTheme, R.style.BlackTheme).forEach { theme ->
                    val host = ContextThemeWrapper(context.createConfigurationContext(configuration), theme)
                    val inflater = LayoutInflater.from(host)
                    val main = inflater.inflate(R.layout.activity_main, null, false)
                    val content = main.findViewById<View>(R.id.main_safe_content)
                    assertTrue("Pages must not expose the startup window background", content.background is ColorDrawable)
                    val background = (content.background as ColorDrawable).color
                    assertEquals(ThemeHelper.resolveColorFromAttr(host, MaterialR.attr.colorSurface), background)
                    listOf(
                        R.layout.list_channel_grid_item to R.id.itemTitleView,
                        R.layout.list_stream_item to R.id.itemTitleView
                    ).forEach { (layout, titleId) ->
                        val row = inflater.inflate(layout, null, false)
                        val title = row.findViewById<TextView>(titleId)
                        assertTrue("Titles must remain readable over the page surface", ColorUtils.calculateContrast(title.currentTextColor, background) >= 4.5)
                    }
                }
            }
        }
    }
}
