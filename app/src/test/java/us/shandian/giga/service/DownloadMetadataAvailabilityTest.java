/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package us.shandian.giga.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import us.shandian.giga.get.FinishedMission;
import us.shandian.giga.get.Mission;

public class DownloadMetadataAvailabilityTest {
    @Test
    public void localDownloadHidesMatchingMetadata() {
        final FinishedMission local = mission(SOURCE, 'v', 100);
        final FinishedMission metadata = mission(SOURCE, 'v', 200);
        final List<Mission> finished = new ArrayList<>(List.of(local));

        DownloadManager.addMissingCompletedDownloadMetadata(
                List.of(),
                finished,
                List.of(metadata)
        );

        assertEquals(1, finished.size());
        assertSame(local, finished.get(0));
    }

    @Test
    public void pendingDownloadHidesMatchingMetadata() {
        final FinishedMission pending = mission(SOURCE, 'a', 100);
        final FinishedMission metadata = mission(SOURCE, 'a', 200);
        final List<Mission> finished = new ArrayList<>();

        DownloadManager.addMissingCompletedDownloadMetadata(
                List.of(pending),
                finished,
                List.of(metadata)
        );

        assertEquals(0, finished.size());
    }

    @Test
    public void differentMediaKindRemainsAvailable() {
        final FinishedMission localAudio = mission(SOURCE, 'a', 100);
        final FinishedMission metadataVideo = mission(SOURCE, 'v', 200);
        final List<Mission> finished = new ArrayList<>(List.of(localAudio));

        DownloadManager.addMissingCompletedDownloadMetadata(
                List.of(),
                finished,
                List.of(metadataVideo)
        );

        assertEquals(2, finished.size());
        assertSame(metadataVideo, finished.get(0));
        assertSame(localAudio, finished.get(1));
    }

    @Test
    public void duplicateMetadataUsesNewestEntry() {
        final FinishedMission newest = mission(SOURCE, 'v', 300);
        final FinishedMission oldest = mission(SOURCE, 'v', 200);
        final List<Mission> finished = new ArrayList<>();

        DownloadManager.addMissingCompletedDownloadMetadata(
                List.of(),
                finished,
                List.of(newest, oldest)
        );

        assertEquals(1, finished.size());
        assertSame(newest, finished.get(0));
    }

    private static FinishedMission mission(
            final String source,
            final char kind,
            final long timestamp
    ) {
        final FinishedMission mission = new FinishedMission();
        mission.source = source;
        mission.kind = kind;
        mission.timestamp = timestamp;
        return mission;
    }

    private static final String SOURCE = "https://www.youtube.com/watch?v=abc";
}
