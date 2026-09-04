/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.subscription;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class FeedGroupDialogNavigationTest {
    private final Path mainDirectory = Files.exists(Path.of("src/main"))
            ? Path.of("src/main") : Path.of("app/src/main");

    @Test
    public void subscriptionPickerProvidesAnAccessibleBackAction() throws Exception {
        final String source = Files.readString(mainDirectory.resolve(
                "java/org/schabi/newpipe/local/subscription/dialog/FeedGroupDialog.kt"));

        assertTrue(source.contains("setNavigationIcon(R.drawable.ic_arrow_back)"));
        assertTrue(source.contains("setNavigationContentDescription(R.string.back)"));
        assertTrue(source.contains("setNavigationOnClickListener { onBackPressed() }"));
    }
}
