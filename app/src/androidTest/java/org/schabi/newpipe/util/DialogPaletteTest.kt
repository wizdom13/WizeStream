/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.util

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.RadioButton
import androidx.appcompat.app.AppCompatDialog
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.R as MaterialR
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R

@RunWith(AndroidJUnit4::class)
class DialogPaletteTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val colorKey = context.getString(R.string.theme_color_key)
    private val themeKey = context.getString(R.string.theme_key)
    private lateinit var previousPreferences: Map<String, String?>
    private val modes = listOf(
        Triple(R.style.LightTheme, R.string.light_theme_key, Configuration.UI_MODE_NIGHT_NO),
        Triple(R.style.DarkTheme, R.string.dark_theme_key, Configuration.UI_MODE_NIGHT_YES),
        Triple(R.style.BlackTheme, R.string.black_theme_key, Configuration.UI_MODE_NIGHT_YES)
    )
    private val paletteRoles = intArrayOf(
        R.attr.colorPrimary,
        MaterialR.attr.colorOnPrimary,
        MaterialR.attr.colorPrimaryContainer,
        MaterialR.attr.colorOnPrimaryContainer,
        MaterialR.attr.colorSecondary,
        MaterialR.attr.colorOnSecondary,
        MaterialR.attr.colorSecondaryContainer,
        MaterialR.attr.colorOnSecondaryContainer
    )

    @Before
    fun savePreferences() {
        previousPreferences = listOf(colorKey, themeKey).associateWith {
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
    }

    @Test
    fun systemDialogsInheritTheHostPaletteAndDownloadControlColors() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            modes.take(2).forEach { mode ->
                val host = host(mode, "follow_system")
                // Model an activity whose colors have already been supplied by Material You.
                host.theme.applyStyle(R.style.ThemeOverlay_wizestream_TestPalette, true)
                dialogThemes(host).forEach { theme ->
                    val dialog = AppCompatDialog(host, theme)
                    try {
                        val themed = dialog.context
                        assertPalette(host, themed)
                        assertColor(host, MaterialR.attr.colorOnSurface, themed, MaterialR.attr.colorOnSurface)
                        assertColor(host, MaterialR.attr.colorOutline, themed, MaterialR.attr.colorOutline)
                        assertColor(host, MaterialR.attr.colorSurfaceContainerHigh, themed, MaterialR.attr.colorSurface)
                        assertColor(themed, MaterialR.attr.colorSurface, themed, android.R.attr.windowBackground)
                        assertColor(themed, MaterialR.attr.colorOnSurface, themed, android.R.attr.textColorPrimary)
                        val content = LayoutInflater.from(themed).inflate(R.layout.download_dialog, null)
                        dialog.setContentView(content)
                        assertTrue("The installed dialog window must float", dialog.window!!.isFloating)
                        val video = content.findViewById<RadioButton>(R.id.video_button)
                        assertEquals(
                            color(host, R.attr.colorPrimary),
                            video.buttonTintList!!.getColorForState(
                                intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked),
                                0
                            )
                        )
                        val alert = MaterialAlertDialogBuilder(themed).setTitle("Playlist").create()
                        assertPalette(host, alert.context)
                        alert.dismiss()
                    } finally {
                        dialog.dismiss()
                    }
                }
            }
        }
    }

    @Test
    fun screenBackgroundsFollowAnUpdatedSurfacePalette() {
        modes.take(2).forEach { mode ->
            val host = host(mode, "follow_system")
            host.theme.applyStyle(R.style.ThemeOverlay_wizestream_TestPalette, true)
            assertColor(host, MaterialR.attr.colorSurface, host, android.R.attr.windowBackground)
            assertColor(host, MaterialR.attr.colorSurface, host, R.attr.windowBackground)
        }
    }

    @Test
    fun blackSurfacesKeepTheirDynamicAccentsInBothDialogSizes() {
        val host = host(modes.last(), "follow_system")
        host.theme.applyStyle(R.style.ThemeOverlay_wizestream_TestPalette, true)
        host.theme.applyStyle(R.style.ThemeOverlay_wizestream_BlackSurfaces, true)
        assertEquals(Color.BLACK, color(host, MaterialR.attr.colorSurface))
        assertEquals(Color.BLACK, color(host, android.R.attr.windowBackground))
        dialogThemes(host).forEach { theme ->
            val dialog = ContextThemeWrapper(host, theme)
            assertPalette(host, dialog)
            assertColor(host, MaterialR.attr.colorSurfaceContainerHigh, dialog, MaterialR.attr.colorSurface)
        }
    }

    @Test
    fun appDefaultAndManualPalettesRemainSelected() {
        modes.forEach { mode ->
            listOf("wizestream", "neutral", "green", "blue", "purple", "orange", "pink", "red").forEach { preset ->
                val host = host(mode, preset)
                ThemeHelper.applyThemeColorOverlay(host)
                dialogThemes(host).forEach { theme ->
                    assertPalette(host, ContextThemeWrapper(host, theme))
                }
            }
        }
    }

    @Test
    fun fallbackPaletteAndMinimumDialogWidthRemainAvailable() {
        modes.forEach { mode ->
            val host = host(mode, "follow_system")
            dialogThemes(host).forEach { theme ->
                assertPalette(host, ContextThemeWrapper(host, theme))
            }
            val dialog = ContextThemeWrapper(host, ThemeHelper.getMinWidthDialogTheme(host))
            listOf(
                android.R.attr.windowMinWidthMajor,
                android.R.attr.windowMinWidthMinor,
                androidx.appcompat.R.attr.windowMinWidthMajor,
                androidx.appcompat.R.attr.windowMinWidthMinor
            ).forEach { attribute ->
                val value = TypedValue()
                assertTrue(dialog.theme.resolveAttribute(attribute, value, true))
                val width = when (value.type) {
                    TypedValue.TYPE_FRACTION -> value.getFraction(1000f, 1000f)
                    TypedValue.TYPE_DIMENSION -> value.getDimension(dialog.resources.displayMetrics)
                    else -> 0f
                }
                assertTrue("Dialog minimum width must be positive", width > 0f)
            }
        }
    }

    private fun host(mode: Triple<Int, Int, Int>, preset: String): ContextThemeWrapper {
        preferences.edit().putString(colorKey, preset)
            .putString(themeKey, context.getString(mode.second)).commit()
        val configuration = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode.third
        }
        return ContextThemeWrapper(context.createConfigurationContext(configuration), mode.first)
    }

    private fun dialogThemes(host: Context) = listOf(
        ThemeHelper.getDialogTheme(host),
        ThemeHelper.getMinWidthDialogTheme(host)
    )

    private fun assertPalette(host: Context, dialog: Context) {
        paletteRoles.forEach { attribute -> assertColor(host, attribute, dialog, attribute) }
    }

    private fun assertColor(source: Context, sourceAttr: Int, target: Context, targetAttr: Int) {
        assertEquals(source.resources.getResourceEntryName(sourceAttr), color(source, sourceAttr), color(target, targetAttr))
    }

    private fun color(context: Context, attribute: Int): Int = ThemeHelper.resolveColorFromAttr(context, attribute)
}
