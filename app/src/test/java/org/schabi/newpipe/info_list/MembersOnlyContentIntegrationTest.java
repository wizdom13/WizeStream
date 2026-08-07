/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.info_list;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class MembersOnlyContentIntegrationTest {
    private final Path projectDirectory = Files.exists(Path.of("src/main"))
            ? Path.of(".") : Path.of("app");

    @Test
    public void everyRemoteStreamLayoutProvidesMembersOnlyBadge() throws Exception {
        for (final String layoutPath : new String[] {
                "src/main/res/layout/list_stream_mini_item.xml",
                "src/main/res/layout/list_stream_item.xml",
                "src/main/res/layout/list_stream_grid_item.xml",
                "src/main/res/layout/list_stream_card_item.xml",
                "src/main/res/layout-land/list_stream_card_item.xml"
        }) {
            final String source = read(layoutPath);
            assertTrue(layoutPath, source.contains("@+id/itemMembersOnlyView"));
            assertTrue(layoutPath, source.contains("@string/members_only"));
            assertTrue(layoutPath, source.contains("android:visibility=\"gone\""));
        }
    }

    @Test
    public void restrictedCardTapShowsExplanationInsteadOfOpeningPlayback() throws Exception {
        final String holder = read(
                "src/main/java/org/schabi/newpipe/info_list/holder/"
                    + "StreamMiniInfoItemHolder.java"
        );
        assertTrue(holder.contains("if (item.requiresMembership())"));
        assertTrue(holder.contains("MembersOnlyContentHelper.showExplanation"));

        final String helper = read(
                "src/main/java/org/schabi/newpipe/util/MembersOnlyContentHelper.java"
        );
        assertTrue(helper.contains("R.string.members_only_explanation"));
        assertTrue(helper.contains("android.R.string.ok"));
    }

    @Test
    public void hidePreferenceFiltersRemoteAndPersistedFeedItems() throws Exception {
        final String settings = read("src/main/res/xml/content_settings.xml");
        assertTrue(settings.contains("@string/hide_members_only_videos_key"));

        final String adapter = read(
                "src/main/java/org/schabi/newpipe/info_list/InfoListAdapter.java"
        );
        assertTrue(adapter.contains("MembersOnlyContentHelper.shouldHide"));
        assertTrue(adapter.contains("requiresMembership()"));

        final String channel = read(
                "src/main/java/org/schabi/newpipe/fragments/list/channel/"
                    + "ChannelTabFragment.java"
        );
        assertTrue(channel.contains("infoListAdapter.getItemsList().isEmpty()"));

        final String feed = read(
                "src/main/java/org/schabi/newpipe/local/feed/FeedFragment.kt"
        );
        assertTrue(feed.contains("hideMembersOnly"));
        assertTrue(feed.contains("stream.requiresMembership"));

        final String entity = read(
                "src/main/java/org/schabi/newpipe/database/stream/model/StreamEntity.kt"
        );
        assertTrue(entity.contains("requiresMembership = item.requiresMembership()"));
        assertTrue(entity.contains("item.setRequiresMembership(requiresMembership)"));
    }

    private String read(final String relativePath) throws Exception {
        return Files.readString(projectDirectory.resolve(relativePath));
    }
}
