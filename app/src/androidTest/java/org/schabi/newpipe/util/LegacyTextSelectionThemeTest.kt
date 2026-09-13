/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.util

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.R as AppCompatR
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R

@RunWith(AndroidJUnit4::class)
class LegacyTextSelectionThemeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val searchFieldIds = listOf(R.id.toolbar_search_edit_text, R.id.contextual_search_edit_text)

    @Test
    fun actualSearchFieldsInheritTheHostAccentAndFrameworkLinkColors() = forEachHost { host ->
        val toolbar = inflateToolbar(host)
        searchFieldIds.forEach { id ->
            val input = toolbar.findViewById<TextView>(id)
            listOf(
                AppCompatR.attr.colorPrimary,
                AppCompatR.attr.colorPrimaryDark,
                AppCompatR.attr.colorAccent,
                AppCompatR.attr.colorControlActivated,
                android.R.attr.textColorLink
            ).forEach { attribute ->
                assertEquals(
                    host.resources.getResourceEntryName(attribute),
                    color(host, AppCompatR.attr.colorPrimary),
                    color(input.context, attribute)
                )
            }
        }
    }

    @Test
    @SdkSuppress(maxSdkVersion = 29)
    @Suppress("DEPRECATION")
    fun legacyCopyToastsInflateFromBothSearchFieldContexts() = forEachHost { host ->
        val toolbar = inflateToolbar(host)
        searchFieldIds.forEach { id ->
            val input = toolbar.findViewById<TextView>(id)
            // Android 9 inflates the system toast in the context of the text being copied.
            val toast = Toast.makeText(input.context, "Text copied", Toast.LENGTH_SHORT)
            val message = checkNotNull(toast.view).findViewById<TextView>(android.R.id.message)
            assertEquals("Text copied", message.text.toString())
        }
    }

    @Test
    fun suggestionRowsAndToolbarPopupsInheritHighlightColors() = forEachHost { host ->
        val toolbar = inflateToolbar(host).findViewById<View>(R.id.toolbar)
        val popup = ContextThemeWrapper(toolbar.context, R.style.ToolbarPopupTheme)
        val suggestion = LayoutInflater.from(host).inflate(R.layout.item_search_suggestion, null, false)
        listOf(popup, suggestion.context).forEach { themed ->
            assertEquals(color(host, AppCompatR.attr.colorControlHighlight), color(themed, AppCompatR.attr.colorControlHighlight))
        }
        assertEquals(color(host, AppCompatR.attr.colorPrimary), color(suggestion.context, android.R.attr.textColorLink))
    }

    private fun inflateToolbar(host: Context): View = LayoutInflater.from(host).inflate(R.layout.toolbar_layout, null, false)

    private fun color(themed: Context, attribute: Int): Int {
        val attributes = themed.obtainStyledAttributes(intArrayOf(attribute))
        return try {
            checkNotNull(attributes.getColorStateList(0)) {
                "Unresolved ${themed.resources.getResourceEntryName(attribute)}"
            }.defaultColor
        } finally {
            attributes.recycle()
        }
    }

    private fun forEachHost(assertions: (Context) -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val modes = listOf(
                R.style.LightTheme to Configuration.UI_MODE_NIGHT_NO,
                R.style.DarkTheme to Configuration.UI_MODE_NIGHT_YES,
                R.style.BlackTheme to Configuration.UI_MODE_NIGHT_YES
            )
            val palettes = listOf(
                0,
                R.style.ThemeOverlay_wizestream_ThemeColor_wizestream,
                R.style.ThemeOverlay_wizestream_ThemeColor_Orange,
                // Model an already-applied dynamic palette without relying on device wallpaper.
                R.style.ThemeOverlay_wizestream_TestPalette
            )
            modes.forEach { (theme, nightMode) ->
                palettes.forEach { palette ->
                    val configuration = Configuration(context.resources.configuration).apply {
                        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
                    }
                    val host = ContextThemeWrapper(context.createConfigurationContext(configuration), theme)
                    if (palette != 0) host.theme.applyStyle(palette, true)
                    assertions(host)
                }
            }
        }
    }
}
