/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nononsenseapps.filepicker.AbstractFilePickerActivity
import com.nononsenseapps.filepicker.AbstractFilePickerFragment
import com.nononsenseapps.filepicker.R as FilePickerR
import com.nononsenseapps.filepicker.Utils
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.streams.io.StoredFileHelper

@RunWith(AndroidJUnit4::class)
class FilePickerCompatibilityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun legacyDirectorySelectionReturnsAnInternalPathWithoutSharingStorage() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val command = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "pm grant ${context.packageName} ${Manifest.permission.WRITE_EXTERNAL_STORAGE}"
            )
            ParcelFileDescriptor.AutoCloseInputStream(command).use { it.readBytes() }
        }
        val directory = File(context.getExternalFilesDir(null), "picker space #é").apply {
            assertTrue(isDirectory || mkdirs())
        }
        val intent = Intent(context, FilePickerActivityHelper::class.java).apply {
            putExtra(AbstractFilePickerActivity.EXTRA_START_PATH, directory.absolutePath)
            putExtra(AbstractFilePickerActivity.EXTRA_MODE, AbstractFilePickerFragment.MODE_DIR)
        }
        try {
            ActivityScenario.launchActivityForResult<FilePickerActivityHelper>(intent).use { scenario ->
                scenario.onActivity { activity ->
                    val fragment = activity.supportFragmentManager.fragments
                        .filterIsInstance<FilePickerActivityHelper.CustomFilePickerFragment>().single()
                    fragment.onClickOk(View(activity))
                }
                val result = scenario.result
                assertEquals(Activity.RESULT_OK, result.resultCode)
                val uri = requireNotNull(result.resultData.data)
                assertTrue(FilePickerActivityHelper.isOwnFileUri(context, uri))
                assertEquals(directory.canonicalFile, Utils.getFileForUri(uri))
                assertFalse(FilePickerActivityHelper.isOwnFileUri(context, uri.buildUpon().authority("${context.packageName}.provider.other").build()))
                assertFalse(uri.scheme == "file" || uri.scheme == "content")
            }
            try {
                FileProvider.getUriForFile(context, "${context.packageName}.provider", directory)
                fail("The update provider must not expose the selected directory")
            } catch (_: IllegalArgumentException) {
                // External storage remains outside the update provider's configured roots.
            }
        } finally {
            directory.delete()
        }
    }

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
