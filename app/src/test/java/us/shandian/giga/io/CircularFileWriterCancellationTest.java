/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package us.shandian.giga.io;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.schabi.newpipe.streams.io.SharpStream;

import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class CircularFileWriterCancellationTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void cancelledWriteStopsBeforePublishingMoreOutput() throws Exception {
        final SharpStream target = mock(SharpStream.class);
        final AtomicBoolean cancelled = new AtomicBoolean();
        final CircularFileWriter writer = new CircularFileWriter(
                target, temporaryFolder.newFile(), () -> -1, cancelled::get);
        cancelled.set(true);

        try {
            writer.write(new byte[]{1});
            fail("Expected cancellation to interrupt the write");
        } catch (final InterruptedIOException expected) {
            verify(target, never()).write(org.mockito.ArgumentMatchers.any(byte[].class),
                    org.mockito.ArgumentMatchers.anyInt(),
                    org.mockito.ArgumentMatchers.anyInt());
        } finally {
            writer.close();
        }
    }
}
