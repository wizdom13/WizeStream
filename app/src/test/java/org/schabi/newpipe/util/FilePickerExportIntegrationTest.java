/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class FilePickerExportIntegrationTest {
    private final Path projectDirectory = Files.exists(Path.of("src/main"))
            ? Path.of(".") : Path.of("app");

    @Test
    public void appDataImportAndExportAlwaysUseSystemDocumentPicker() throws Exception {
        final String subscriptions = read(
                "src/main/java/org/schabi/newpipe/local/subscription/"
                        + "SubscriptionsImportExportHelper.kt");
        final String backupRestore = read(
                "src/main/java/org/schabi/newpipe/settings/BackupRestoreSettingsFragment.java");

        for (final String source : new String[] {subscriptions, backupRestore}) {
            assertTrue(source.contains("StoredFileHelper.getSystemPicker("));
            assertTrue(source.contains("StoredFileHelper.getNewSystemPicker("));
            assertFalse(source.contains("StoredFileHelper.getPicker("));
            assertFalse(source.contains("StoredFileHelper.getNewPicker("));
        }
    }

    @Test
    public void legacyPickerUsesAppCompatToolbarOverlayWithExplicitTextColors()
            throws Exception {
        final String styles = read("src/main/res/values/styles_misc.xml");

        assertTrue(styles.contains(
                "name=\"FilePickerToolbarTheme\" parent=\"ThemeOverlay.AppCompat.ActionBar\""));
        assertTrue(styles.contains("name=\"android:textColorPrimary\">@color/white"));
        assertTrue(styles.contains("name=\"android:textColorSecondary\">@color/white_secondary"));
        assertFalse(styles.contains("name=\"nnf_toolbarTheme\">@style/ToolbarTheme"));
    }

    private String read(final String relativePath) throws Exception {
        return Files.readString(projectDirectory.resolve(relativePath));
    }
}
