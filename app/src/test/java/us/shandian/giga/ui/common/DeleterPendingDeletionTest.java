/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package us.shandian.giga.ui.common;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.Test;

import us.shandian.giga.get.DownloadMission;
import us.shandian.giga.postprocessing.Postprocessing;
import us.shandian.giga.service.DownloadManager;
import us.shandian.giga.ui.common.Deleter.PendingDeletion;

public class DeleterPendingDeletionTest {
    @Test
    public void runningPostprocessingRequiresImmediateIrreversibleDeletion() {
        final DownloadMission mission = mock(DownloadMission.class);
        mission.psAlgorithm = mock(Postprocessing.class);
        mission.psState = 1;

        org.junit.Assert.assertTrue(Deleter.requiresImmediateDeletion(mission));
    }

    @Test
    public void runningDownloadIsPausedAndRestored() {
        final DownloadMission mission = mock(DownloadMission.class);
        final DownloadManager manager = mock(DownloadManager.class);
        mission.running = true;
        mission.enqueued = true;

        final PendingDeletion deletion = PendingDeletion.capture(mission, true);
        deletion.suspend(manager);
        deletion.restore(manager);

        verify(manager).pauseMission(mission);
        verify(mission).setEnqueued(true);
        verify(manager).resumeMission(mission);
    }

    @Test
    public void queuedDownloadIsDequeuedAndRestoredWithoutStarting() {
        final DownloadMission mission = mock(DownloadMission.class);
        final DownloadManager manager = mock(DownloadManager.class);
        mission.running = false;
        mission.enqueued = true;

        final PendingDeletion deletion = PendingDeletion.capture(mission, false);
        deletion.suspend(manager);
        deletion.restore(manager);

        verify(mission).setEnqueued(false);
        verify(mission).setEnqueued(true);
        verify(manager, never()).pauseMission(mission);
        verify(manager, never()).resumeMission(mission);
    }

    @Test
    public void deletedDownloadIsNotRestored() {
        final DownloadMission mission = mock(DownloadMission.class);
        final DownloadManager manager = mock(DownloadManager.class);
        mission.running = true;
        mission.enqueued = true;
        mission.deleted = true;

        final PendingDeletion deletion = PendingDeletion.capture(mission, true);
        deletion.restore(manager);

        verify(mission, never()).setEnqueued(true);
        verify(manager, never()).resumeMission(mission);
    }
}
