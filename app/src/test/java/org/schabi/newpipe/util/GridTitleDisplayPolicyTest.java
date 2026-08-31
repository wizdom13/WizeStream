/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class GridTitleDisplayPolicyTest {
    private final Path repositoryRoot =
            Files.exists(Path.of("app/src/main/res/xml/appearance_settings.xml"))
                    ? Path.of(".") : Path.of("..");

    @Test
    public void compactModeKeepsTwoLinesAndEllipsizing() {
        assertEquals(2, GridTitleDisplayPolicy.maxLines(false));
        assertTrue(GridTitleDisplayPolicy.shouldEllipsize(false));
    }

    @Test
    public void fullModeAllowsUnlimitedLinesWithoutEllipsizing() {
        assertEquals(Integer.MAX_VALUE, GridTitleDisplayPolicy.maxLines(true));
        assertFalse(GridTitleDisplayPolicy.shouldEllipsize(true));
    }

    @Test
    public void settingIsDisabledByDefault() throws Exception {
        final String appearanceSettings = read(
                "app/src/main/res/xml/appearance_settings.xml");

        assertTrue(appearanceSettings.contains(
                "android:key=\"@string/show_full_grid_titles_key\""));
        assertTrue(appearanceSettings.contains(
                "android:defaultValue=\"false\""));
    }

    @Test
    public void everyStreamGridOrCardHolderAppliesTheDisplayPolicy() throws Exception {
        final List<String> streamHolders = List.of(
                "app/src/main/java/org/schabi/newpipe/info_list/holder/"
                        + "StreamGridInfoItemHolder.java",
                "app/src/main/java/org/schabi/newpipe/info_list/holder/"
                        + "StreamCardInfoItemHolder.java",
                "app/src/main/java/org/schabi/newpipe/local/holder/"
                        + "LocalPlaylistStreamGridItemHolder.java",
                "app/src/main/java/org/schabi/newpipe/local/holder/"
                        + "LocalStatisticStreamGridItemHolder.java");

        for (final String streamHolder : streamHolders) {
            assertTrue(read(streamHolder).contains(
                    "GridTitleDisplayPolicy.apply(itemVideoTitleView);"));
        }
    }

    @Test
    public void channelGroupGridAndCardItemsApplyTheDisplayPolicy() throws Exception {
        final String streamItem = read(
                "app/src/main/java/org/schabi/newpipe/local/feed/item/StreamItem.kt");

        assertTrue(streamItem.contains(
                "itemVersion == ItemVersion.GRID || itemVersion == ItemVersion.CARD"));
        assertTrue(streamItem.contains(
                "GridTitleDisplayPolicy.apply(viewBinding.itemVideoTitleView)"));
    }

    private String read(final String relativePath) throws Exception {
        return Files.readString(repositoryRoot.resolve(relativePath));
    }
}
