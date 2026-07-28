/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package us.shandian.giga.service;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileNotFoundException;

public class PendingDownloadRequestStoreTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void largeRequestIsStoredOutsideBinderAndConsumedOnce() throws Exception {
        final byte[] request = new byte[1_400_000];
        request[0] = 1;
        request[request.length - 1] = 2;

        final String token = PendingDownloadRequestStore.write(
                temporaryFolder.getRoot(), request);
        final File requestFile = PendingDownloadRequestStore.getRequestFile(
                temporaryFolder.getRoot(), token);

        assertTrue(token.length() < 100);
        assertTrue(requestFile.length() > 1_000_000);
        assertArrayEquals(request, PendingDownloadRequestStore.take(
                temporaryFolder.getRoot(), token, byte[].class));
        assertFalse(requestFile.exists());
        assertThrows(FileNotFoundException.class, () -> PendingDownloadRequestStore.take(
                temporaryFolder.getRoot(), token, byte[].class));
    }

    @Test
    public void explicitCleanupRemovesUnstartedRequest() throws Exception {
        final String token = PendingDownloadRequestStore.write(
                temporaryFolder.getRoot(), "request");
        final File requestFile = PendingDownloadRequestStore.getRequestFile(
                temporaryFolder.getRoot(), token);

        PendingDownloadRequestStore.delete(temporaryFolder.getRoot(), token);

        assertFalse(requestFile.exists());
    }

    @Test
    public void invalidTokenCannotEscapePrivateRequestDirectory() {
        assertThrows(IllegalArgumentException.class, () ->
                PendingDownloadRequestStore.getRequestFile(
                        temporaryFolder.getRoot(), "../outside"));
    }
}
