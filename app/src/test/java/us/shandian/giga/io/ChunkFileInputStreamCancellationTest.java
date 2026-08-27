/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package us.shandian.giga.io;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.schabi.newpipe.streams.io.SharpStream;

import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChunkFileInputStreamCancellationTest {
    @Test
    public void cancelledReadStopsBeforeConsumingMoreInput() throws Exception {
        final SharpStream source = mock(SharpStream.class);
        when(source.length()).thenReturn(16L);
        final AtomicBoolean cancelled = new AtomicBoolean();
        final ChunkFileInputStream input = new ChunkFileInputStream(
                source, 0, 16, null, cancelled::get);
        cancelled.set(true);

        try {
            input.read(new byte[1]);
            fail("Expected cancellation to interrupt the read");
        } catch (final InterruptedIOException expected) {
            verify(source, never()).read(org.mockito.ArgumentMatchers.any(byte[].class),
                    org.mockito.ArgumentMatchers.anyInt(),
                    org.mockito.ArgumentMatchers.anyInt());
        } finally {
            input.close();
        }
    }
}
