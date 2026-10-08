package org.schabi.newpipe.fragments.list.channel

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.LayoutInflater
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.graphics.ColorUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.R as MaterialR
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FragmentChannelBinding

@RunWith(AndroidJUnit4::class)
class ChannelSubscribeButtonPaletteTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val modes = listOf(R.style.LightTheme, R.style.DarkTheme, R.style.BlackTheme)

    @Test
    fun subscriptionStatesRemainReadableInLightDarkAndBlackThemes() {
        instrumentation.runOnMainSync {
            modes.forEach { style -> assertPalette(base(style), style == R.style.BlackTheme) }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 31)
    fun subscriptionStatesUseDynamicColorsAndKeepTheBlackSurface() {
        assumeTrue(DynamicColors.isDynamicColorAvailable())
        instrumentation.runOnMainSync {
            modes.forEach { style ->
                val palette = DynamicColors.wrapContextIfAvailable(
                    base(style),
                    if (style == R.style.LightTheme) {
                        MaterialR.style.ThemeOverlay_Material3_DynamicColors_Light
                    } else {
                        MaterialR.style.ThemeOverlay_Material3_DynamicColors_Dark
                    }
                )
                if (style == R.style.BlackTheme) {
                    palette.theme.applyStyle(R.style.ThemeOverlay_wizestream_BlackSurfaces, true)
                }
                assertPalette(palette, style == R.style.BlackTheme)
            }
        }
    }

    private fun base(style: Int): Context {
        val configuration = Configuration(instrumentation.targetContext.resources.configuration)
        configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (style == R.style.LightTheme) Configuration.UI_MODE_NIGHT_NO else Configuration.UI_MODE_NIGHT_YES
        return ContextThemeWrapper(instrumentation.targetContext.createConfigurationContext(configuration), style)
    }

    private fun assertPalette(palette: Context, black: Boolean) {
        val button = FragmentChannelBinding.inflate(LayoutInflater.from(palette)).channelSubscribeButton
        listOf(false, true, false).forEach { subscribed ->
            button.isActivated = subscribed
            val background = button.backgroundTintList!!.getColorForState(button.drawableState, 0)
            val expectedBackground = color(palette, if (subscribed) MaterialR.attr.colorSecondaryContainer else MaterialR.attr.colorSurface)
            val expectedText = color(palette, if (subscribed) MaterialR.attr.colorOnSecondaryContainer else MaterialR.attr.colorPrimary)
            assertEquals(expectedBackground, background)
            assertEquals(expectedText, button.currentTextColor)
            assertEquals(color(palette, MaterialR.attr.colorPrimary), button.strokeColor!!.defaultColor)
            assertTrue("Subscribe button contrast must be at least 4.5:1", ColorUtils.calculateContrast(button.currentTextColor, background) >= 4.5)
            if (black && !subscribed) assertEquals(Color.BLACK, background)
        }
    }

    private fun color(context: Context, attribute: Int): Int = MaterialColors.getColor(context, attribute, "Subscribe button")
}
