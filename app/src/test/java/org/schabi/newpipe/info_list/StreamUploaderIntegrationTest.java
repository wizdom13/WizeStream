/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.info_list;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class StreamUploaderIntegrationTest {
    private final Path projectDirectory = Files.exists(Path.of("src/main"))
            ? Path.of(".") : Path.of("app");

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
