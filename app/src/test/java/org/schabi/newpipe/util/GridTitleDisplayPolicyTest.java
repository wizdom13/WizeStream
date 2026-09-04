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
    public void compactDetailModeKeepsOneLineAndEllipsizing() {
        assertEquals(1, GridTitleDisplayPolicy.maxLines(false, false, 1));
        assertTrue(GridTitleDisplayPolicy.shouldEllipsize(false, false));
    }

    @Test
    public void manuallyExpandedDetailAllowsUnlimitedLines() {
        assertEquals(Integer.MAX_VALUE, GridTitleDisplayPolicy.maxLines(false, true, 1));
        assertFalse(GridTitleDisplayPolicy.shouldEllipsize(false, true));
    }

    @Test
    public void fullDetailModeStaysExpandedWhenSecondaryControlsClose() {
        assertEquals(Integer.MAX_VALUE, GridTitleDisplayPolicy.maxLines(true, false, 1));
        assertFalse(GridTitleDisplayPolicy.shouldEllipsize(true, false));
    }

    @Test
    public void compactLocalDetailModeKeepsThreeLines() {
        assertEquals(3, GridTitleDisplayPolicy.maxLines(false, false, 3));
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
    public void everyFullTitleStreamHolderAppliesTheDisplayPolicy() throws Exception {
        final List<String> streamHolders = List.of(
                "app/src/main/java/org/schabi/newpipe/info_list/holder/"
                        + "StreamGridInfoItemHolder.java",
                "app/src/main/java/org/schabi/newpipe/info_list/holder/"
                        + "StreamCardInfoItemHolder.java",
                "app/src/main/java/org/schabi/newpipe/info_list/holder/"
                        + "StreamWideRelatedInfoItemHolder.java",
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
    public void wideRelatedVideosUseTheFullTitleAwareHolder() throws Exception {
        final String adapter = read(
                "app/src/main/java/org/schabi/newpipe/info_list/InfoListAdapter.java");

        assertTrue(adapter.contains("case WIDE_RELATED_STREAM_HOLDER_TYPE:"));
        assertTrue(adapter.contains(
                "return new StreamWideRelatedInfoItemHolder(infoItemBuilder, parent);"));
        assertFalse(adapter.contains(
                "R.layout.list_stream_related_wide_item, parent"));
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

    @Test
    public void videoDetailsApplyTheDisplayPolicyAcrossEveryState() throws Exception {
        final String videoDetailFragment = read(
                "app/src/main/java/org/schabi/newpipe/fragments/detail/VideoDetailFragment.java");

        assertTrue(videoDetailFragment.contains(
                "applyTitleDisplayPolicy(expandSecondaryControls);"));
        assertTrue(videoDetailFragment.contains(
                "GridTitleDisplayPolicy.applyToLocalDetail(binding.detailVideoTitleView);"));
        assertTrue(videoDetailFragment.contains(
                "binding.detailSecondaryControlPanel.getVisibility() != View.GONE"));
        assertEquals(4, occurrences(videoDetailFragment,
                "applyTitleDisplayPolicy(false);"));
        assertFalse(videoDetailFragment.contains("detailVideoTitleView.setMaxLines"));
    }

    @Test
    public void settingCopyCoversGridsAndVideoDetails() throws Exception {
        final String strings = read("app/src/main/res/values/strings.xml");

        assertTrue(strings.contains(
                "<string name=\"show_full_grid_titles_title\">"
                        + "Show full video titles</string>"));
        assertTrue(strings.contains(
                "Display complete titles in grids and while watching videos"));
    }

    private int occurrences(final String value, final String target) {
        return (value.length() - value.replace(target, "").length()) / target.length();
    }

    private String read(final String relativePath) throws Exception {
        return Files.readString(repositoryRoot.resolve(relativePath));
    }
}
