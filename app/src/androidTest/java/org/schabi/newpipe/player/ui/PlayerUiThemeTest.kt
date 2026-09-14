/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.R as MaterialR
import com.google.android.material.color.DynamicColors
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.PlayerBinding
import org.schabi.newpipe.util.ThemeHelper

@RunWith(AndroidJUnit4::class)
class PlayerUiThemeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val colorKey = context.getString(R.string.theme_color_key)
    private val themeKey = context.getString(R.string.theme_key)
    private val nightKey = context.getString(R.string.night_theme_key)
    private lateinit var previousPreferences: Map<String, String?>
    private val modes = listOf(
        Triple(R.style.LightTheme, R.string.light_theme_key, Configuration.UI_MODE_NIGHT_NO),
        Triple(R.style.DarkTheme, R.string.dark_theme_key, Configuration.UI_MODE_NIGHT_YES),
        Triple(R.style.BlackTheme, R.string.black_theme_key, Configuration.UI_MODE_NIGHT_YES)
    )

    @Before
    fun savePreferences() {
        previousPreferences = listOf(colorKey, themeKey, nightKey).associateWith {
            preferences.getString(it, null)
        }
    }

    @After
    fun restorePreferences() {
        preferences.edit().apply {
            previousPreferences.forEach { (key, value) ->
                if (value == null) remove(key) else putString(key, value)
            }
        }.commit()
        instrumentation.waitForIdleSync()
    }

    @Test
    fun realPlayerSeekBarUsesTheActivePaletteInsteadOfTheLegacyGreenRole() {
        instrumentation.runOnMainSync {
            modes.forEach { mode ->
                val palette = base(mode).apply {
                    theme.applyStyle(R.style.ThemeOverlay_wizestream_TestPlayerPalette, true)
                    if (mode.first == R.style.BlackTheme) {
                        theme.applyStyle(R.style.ThemeOverlay_wizestream_BlackSurfaces, true)
                    }
                }
                val binding = PlayerBinding.inflate(LayoutInflater.from(palette))
                PlayerUiTheme.applyColors(binding.playbackSeekBar, binding.playbackSeekBar.context)
                assertNotEquals(color(palette, R.attr.colorPrimaryFixedDim), color(palette, R.attr.colorPrimary))
                assertEquals(Color.rgb(103, 80, 164), binding.playbackSeekBar.thumbTintList!!.defaultColor)
                assertColors(binding.playbackSeekBar, palette)
                if (mode.first == R.style.BlackTheme) assertBlackSurfaces(palette)
            }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 31)
    fun dynamicColorsReachPlayerControlsWithoutAnActivityAndKeepBlackSurfaces() {
        assumeTrue(DynamicColors.isDynamicColorAvailable())
        instrumentation.runOnMainSync {
            // Like PlayerService, this source has a static theme and no Activity owner.
            val serviceContext = ContextThemeWrapper(context, R.style.DarkTheme)
            val originalColor = color(serviceContext, R.attr.colorPrimary)
            modes.forEach { mode ->
                select(mode, "follow_system")
                val expected = DynamicColors.wrapContextIfAvailable(
                    base(mode),
                    if (mode.first == R.style.LightTheme) {
                        MaterialR.style.ThemeOverlay_Material3_DynamicColors_Light
                    } else {
                        MaterialR.style.ThemeOverlay_Material3_DynamicColors_Dark
                    }
                )
                val palette = PlayerUiTheme.createContext(serviceContext)
                val binding = PlayerBinding.inflate(LayoutInflater.from(palette))
                PlayerUiTheme(serviceContext, binding.playbackSeekBar).use {
                    assertColors(binding.playbackSeekBar, expected)
                    assertEquals(color(expected, R.attr.colorPrimary), color(binding.playbackSeekBar.context, R.attr.colorPrimary))
                    assertEquals(originalColor, color(serviceContext, R.attr.colorPrimary))
                    if (mode.first == R.style.BlackTheme) assertBlackSurfaces(palette)
                }
            }
        }
    }

    @Test
    fun manualPalettesUseTheSelectedModeEvenWhenTheServiceConfigurationDisagrees() {
        instrumentation.runOnMainSync {
            modes.forEach { mode ->
                listOf("wizestream", "blue", "purple", "orange").forEach { preset ->
                    select(mode, preset)
                    val expected = base(mode).also(ThemeHelper::applyThemeColorOverlay)
                    val oppositeMode = if (mode.first == R.style.LightTheme) modes[1] else modes[0]
                    val source = base(oppositeMode)
                    val palette = PlayerUiTheme.createContext(source)
                    val binding = PlayerBinding.inflate(LayoutInflater.from(palette))
                    PlayerUiTheme(source, binding.playbackSeekBar).use {
                        assertColors(binding.playbackSeekBar, expected)
                        assertEquals(mode.third, palette.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                        if (mode.first == R.style.BlackTheme) assertBlackSurfaces(palette)
                    }
                }
            }
        }
    }

    @Test
    @SdkSuppress(maxSdkVersion = 30)
    fun olderAndroidVersionsKeepTheFallbackPalette() {
        instrumentation.runOnMainSync {
            modes.forEach { mode ->
                select(mode, "follow_system")
                val binding = PlayerBinding.inflate(LayoutInflater.from(PlayerUiTheme.createContext(context)))
                PlayerUiTheme(context, binding.playbackSeekBar).use {
                    assertColors(binding.playbackSeekBar, base(mode))
                }
            }
        }
    }

    @Test
    fun automaticNightModeUsesTheBlackPreferenceWithoutLosingAccents() {
        instrumentation.runOnMainSync {
            preferences.edit().putString(colorKey, "purple")
                .putString(themeKey, context.getString(R.string.auto_device_theme_key))
                .putString(nightKey, context.getString(R.string.black_theme_key)).commit()
            val palette = PlayerUiTheme.createContext(base(modes[1]))
            val expected = base(modes[2]).also(ThemeHelper::applyThemeColorOverlay)
            assertBlackSurfaces(palette)
            assertEquals(color(expected, R.attr.colorPrimary), color(palette, R.attr.colorPrimary))
            val daytime = PlayerUiTheme.createContext(base(modes[0]))
            assertNotEquals(Color.BLACK, color(daytime, MaterialR.attr.colorSurface))
        }
    }

    @Test
    fun existingControlsRefreshWhenPreferencesChangeAndStopListeningWhenDestroyed() {
        lateinit var binding: PlayerBinding
        lateinit var controller: PlayerUiTheme
        var retainedColor = 0
        instrumentation.runOnMainSync {
            select(modes[0], "purple")
            binding = PlayerBinding.inflate(LayoutInflater.from(PlayerUiTheme.createContext(context)))
            binding.playbackSeekBar.apply {
                max = 1000
                progress = 321
                secondaryProgress = 654
            }
            controller = PlayerUiTheme(context, binding.playbackSeekBar)
        }
        try {
            val initialColor = binding.playbackSeekBar.progressTintList!!.defaultColor
            preferences.edit().putString(colorKey, "orange").commit()
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertNotEquals(initialColor, binding.playbackSeekBar.progressTintList!!.defaultColor)
                assertColors(binding.playbackSeekBar, base(modes[0]).also(ThemeHelper::applyThemeColorOverlay))
            }
            val lightColor = binding.playbackSeekBar.progressTintList!!.defaultColor
            preferences.edit().putString(themeKey, context.getString(R.string.dark_theme_key)).commit()
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertNotEquals(lightColor, binding.playbackSeekBar.progressTintList!!.defaultColor)
                assertColors(binding.playbackSeekBar, base(modes[1]).also(ThemeHelper::applyThemeColorOverlay))
                assertPosition(binding.playbackSeekBar)
                retainedColor = binding.playbackSeekBar.progressTintList!!.defaultColor
                // Cancel an already queued refresh before the old UI releases the shared view.
                preferences.edit().putString(colorKey, "blue").commit()
                controller.close()
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertEquals(retainedColor, binding.playbackSeekBar.progressTintList!!.defaultColor)
            }
            preferences.edit().putString(colorKey, "purple").commit()
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertEquals(retainedColor, binding.playbackSeekBar.progressTintList!!.defaultColor)
                PlayerUiTheme(context, binding.playbackSeekBar).use {
                    assertColors(binding.playbackSeekBar, base(modes[1]).also(ThemeHelper::applyThemeColorOverlay))
                    assertPosition(binding.playbackSeekBar)
                }
            }
        } finally {
            instrumentation.runOnMainSync { controller.close() }
        }
    }

    @Test
    fun sharedPlayerViewKeepsItsPaletteAndPositionWhenResizedAndReparented() {
        instrumentation.runOnMainSync {
            select(modes[2], "purple")
            val palette = PlayerUiTheme.createContext(context)
            val binding = PlayerBinding.inflate(LayoutInflater.from(palette))
            binding.playbackSeekBar.apply {
                max = 1000
                progress = 321
                secondaryProgress = 654
            }
            val seekBar = binding.playbackSeekBar
            // Inline, fullscreen and popup share this same player layout and seek bar.
            // Exercise its reuse without starting network playback or requiring overlay permission.
            listOf(480 to 270, 960 to 540, 320 to 180).forEach { (width, height) ->
                val host = FrameLayout(palette)
                host.addView(binding.root)
                host.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                )
                host.layout(0, 0, width, height)
                PlayerUiTheme(context, seekBar).use { controller ->
                    controller.refresh()
                    assertSame(seekBar, binding.playbackSeekBar)
                    assertColors(seekBar, palette)
                    assertPosition(seekBar)
                }
                host.removeView(binding.root)
            }
        }
    }

    private fun select(mode: Triple<Int, Int, Int>, preset: String) {
        preferences.edit().putString(colorKey, preset)
            .putString(themeKey, context.getString(mode.second)).commit()
    }

    private fun base(mode: Triple<Int, Int, Int>): ContextThemeWrapper {
        val configuration = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode.third
        }
        return ContextThemeWrapper(context.createConfigurationContext(configuration), mode.first)
    }

    private fun assertColors(seekBar: SeekBar, palette: Context) {
        assertEquals(color(palette, R.attr.colorPrimary), seekBar.progressTintList!!.defaultColor)
        assertEquals(color(palette, R.attr.colorPrimary), seekBar.thumbTintList!!.defaultColor)
        assertEquals(color(palette, MaterialR.attr.colorPrimaryContainer), seekBar.secondaryProgressTintList!!.defaultColor)
        assertEquals(color(palette, MaterialR.attr.colorSurfaceVariant), seekBar.progressBackgroundTintList!!.defaultColor)
        assertEquals(PorterDuff.Mode.SRC_IN, seekBar.progressTintMode)
        assertEquals(PorterDuff.Mode.SRC_IN, seekBar.thumbTintMode)
    }

    private fun assertBlackSurfaces(palette: Context) {
        assertEquals(Color.BLACK, color(palette, MaterialR.attr.colorSurface))
        assertEquals(Color.BLACK, color(palette, android.R.attr.windowBackground))
    }

    private fun assertPosition(seekBar: SeekBar) {
        assertEquals(1000, seekBar.max)
        assertEquals(321, seekBar.progress)
        assertEquals(654, seekBar.secondaryProgress)
    }

    private fun color(palette: Context, attribute: Int) = ThemeHelper.resolveColorFromAttr(palette, attribute)
}
