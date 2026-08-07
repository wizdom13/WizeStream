/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.extractor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;
import org.schabi.newpipe.extractor.stream.StreamType;

public class MembersOnlyFilterTest {
    @Test
    public void paidContentFilterRemovesOnlyMembershipRestrictedVideos() {
        final StreamInfoItem publicVideo = stream("Public video", false);
        final StreamInfoItem membersOnlyVideo = stream("Members-only video", true);
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(0);
        collector.addAll(List.of(publicVideo, membersOnlyVideo));

        collector.applyBlocking(new InfoItemsCollector.FilterConfig(
                new ArrayList<>(), new ArrayList<>(), false, true));

        assertEquals(1, collector.getItems().size());
        assertSame(publicVideo, collector.getItems().get(0));
    }

    private static StreamInfoItem stream(final String name, final boolean requiresMembership) {
        final StreamInfoItem item = new StreamInfoItem(
                0, "https://example.com/" + name, name, StreamType.VIDEO_STREAM);
        item.setRequiresMembership(requiresMembership);
        return item;
    }
}
