/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package us.shandian.giga.get;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import us.shandian.giga.postprocessing.Postprocessing;

public class DownloadMissionDeletionTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void cancelWithoutDeletingFileStopsEveryWorkerAndPreservesOutput()
            throws Exception {
        final TestMission testMission = mission();
        final Thread initializer = mock(Thread.class);
        final Thread worker = mock(Thread.class);
        final Postprocessing postprocessing = mock(Postprocessing.class);
        testMission.mission.init = initializer;
        testMission.mission.threads = new Thread[]{worker};
        testMission.mission.psAlgorithm = postprocessing;
        testMission.mission.running = true;
        testMission.mission.enqueued = true;

        assertTrue(testMission.mission.cancel(false));

        assertFalse(testMission.mission.running);
        assertFalse(testMission.mission.enqueued);
        assertTrue(testMission.mission.deleted);
        assertFalse(testMission.metadata.exists());
        verify(initializer).interrupt();
        verify(worker).interrupt();
        verify(postprocessing).cleanupTemporalDir();
        verify(testMission.storage, never()).delete();
    }

    @Test
    public void cancelWithDeletingFileRemovesOutput() throws Exception {
        final TestMission testMission = mission();
        when(testMission.storage.delete()).thenReturn(true);

        assertTrue(testMission.mission.cancel(true));

        assertTrue(testMission.mission.deleted);
        assertFalse(testMission.metadata.exists());
        verify(testMission.storage).delete();
    }

    @Test
    public void cancelDuringPostprocessingDefersTemporaryCleanupToWorker() throws Exception {
        final TestMission testMission = mission();
        final Thread worker = mock(Thread.class);
        final Postprocessing postprocessing = mock(Postprocessing.class);
        testMission.mission.threads = new Thread[]{worker};
        testMission.mission.psAlgorithm = postprocessing;
        testMission.mission.psState = 1;

        assertTrue(testMission.mission.cancel(false));

        verify(worker).interrupt();
        verify(postprocessing, never()).cleanupTemporalDir();
    }

    @Test
    public void deletedPostprocessingNeverTransitionsToCompleted() {
        assertEquals(0, DownloadMission.resolvePostprocessingFinalState(
                true, DownloadMission.ERROR_NOTHING));
        assertEquals(2, DownloadMission.resolvePostprocessingFinalState(
                false, DownloadMission.ERROR_NOTHING));
        assertEquals(0, DownloadMission.resolvePostprocessingFinalState(
                false, DownloadMission.ERROR_POSTPROCESSING));
    }

    @Test
    public void repeatedCancellationDoesNotDeleteOutputTwice() throws Exception {
        final TestMission testMission = mission();
        when(testMission.storage.delete()).thenReturn(true);

        assertTrue(testMission.mission.cancel(true));
        assertTrue(testMission.mission.cancel(true));

        verify(testMission.storage).delete();
    }

    private TestMission mission() throws Exception {
        final org.schabi.newpipe.streams.io.StoredFileHelper storage =
                mock(org.schabi.newpipe.streams.io.StoredFileHelper.class);
        final DownloadMission mission =
                new DownloadMission(new String[]{"https://example.com/video"}, storage, 'v', null);
        final File metadata = temporaryFolder.newFile();
        mission.metadata = metadata;
        return new TestMission(mission, storage, metadata);
    }

    private static final class TestMission {
        private final DownloadMission mission;
        private final org.schabi.newpipe.streams.io.StoredFileHelper storage;
        private final File metadata;

        private TestMission(
                final DownloadMission mission,
                final org.schabi.newpipe.streams.io.StoredFileHelper storage,
                final File metadata
        ) {
            this.mission = mission;
            this.storage = storage;
            this.metadata = metadata;
        }
    }
}
