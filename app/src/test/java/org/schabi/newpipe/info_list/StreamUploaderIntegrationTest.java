/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.info_list;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class StreamUploaderIntegrationTest {
    private final Path projectDirectory = Files.exists(Path.of("src/main"))
            ? Path.of(".") : Path.of("app");

    @Test
    public void standardLayoutsExposeAccessibleUploaderTargetAndAvatar() throws Exception {
        for (final String layout : new String[] {
                "list_stream_item.xml",
                "list_stream_grid_item.xml",
                "list_stream_card_item.xml"
        }) {
            final String source = read("src/main/res/layout/" + layout);
            assertTrue(source.contains("@+id/itemUploaderRoot"));
            assertTrue(source.contains("@+id/itemUploaderAvatarView"));
            assertTrue(source.contains("@dimen/stream_item_uploader_touch_target"));
            assertTrue(source.contains("@style/CircularImageView"));
        }
    }

    @Test
    public void compactMiniLayoutDoesNotAddAvatar() throws Exception {
        final String source = read("src/main/res/layout/list_stream_mini_item.xml");
        assertFalse(source.contains("itemUploaderAvatarView"));
    }

    @Test
    public void recycledSearchAndFeedBindingsClearUploaderInteraction() throws Exception {
        final String holder = read(
                "src/main/java/org/schabi/newpipe/info_list/holder/StreamMiniInfoItemHolder.java"
        );
        final String feedItem = read(
                "src/main/java/org/schabi/newpipe/local/feed/item/StreamItem.kt"
        );

        for (final String source : new String[] {holder, feedItem}) {
            assertTrue(source.contains("setOnClickListener(null)"));
            assertTrue(source.contains("setContentDescription(null)")
                    || source.contains("contentDescription = null"));
            assertTrue(source.contains("setClickable(false)")
                    || source.contains("isClickable = false"));
        }
    }

    @Test
    public void feedRefreshAndImportUseCachedSubscriptionAvatarWithoutExtraLookup()
            throws Exception {
        final String feedLoadManager = read(
                "src/main/java/org/schabi/newpipe/local/feed/service/FeedLoadManager.kt"
        );
        final String subscriptionManager = read(
                "src/main/java/org/schabi/newpipe/local/subscription/SubscriptionManager.kt"
        );
        assertTrue(feedLoadManager.contains("uploaderAvatarUrl = info.avatarUrl"));
        assertTrue(subscriptionManager.contains(
                "uploaderAvatarUrl = listEntities[index].avatarUrl"
        ));
    }

    private String read(final String relativePath) throws Exception {
        return Files.readString(projectDirectory.resolve(relativePath));
    }
}
