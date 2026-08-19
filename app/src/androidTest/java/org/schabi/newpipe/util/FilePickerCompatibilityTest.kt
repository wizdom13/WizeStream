/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.util

import android.content.Context
import android.content.Intent
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nononsenseapps.filepicker.R as FilePickerR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.streams.io.StoredFileHelper

@RunWith(AndroidJUnit4::class)
class FilePickerCompatibilityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun legacyFilePickerLayoutInflatesWithBothAppThemes() {
        listOf(R.style.FilePickerThemeLight, R.style.FilePickerThemeDark).forEach { theme ->
            val themedContext = ContextThemeWrapper(context, theme)
            val layout = LayoutInflater.from(themedContext)
                .inflate(FilePickerR.layout.nnf_fragment_filepicker, null, false)

            assertNotNull(layout.findViewById(FilePickerR.id.nnf_current_dir))
        }
    }

    @Test
    fun systemExportPickerIgnoresLegacyStoragePreference() {
        val intent = StoredFileHelper.getNewSystemPicker(
            context,
            "WizeStreamData.zip",
            "application/zip",
            null
        )

        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("application/zip", intent.type)
        assertEquals("WizeStreamData.zip", intent.getStringExtra(Intent.EXTRA_TITLE))
        assertNull(intent.component)
    }

    @Test
    fun systemImportPickerIgnoresLegacyStoragePreference() {
        val intent = StoredFileHelper.getSystemPicker(context, "application/zip", null)

        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertEquals("application/zip", intent.type)
        assertNull(intent.component)
    }
}
