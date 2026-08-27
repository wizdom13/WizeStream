/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import okhttp3.RequestBody;
import okio.Buffer;

public class DownloaderImplTest {
    @Test
    public void postWithoutDataUsesEmptyRequestBody() throws IOException {
        final RequestBody requestBody = DownloaderImpl.buildRequestBody("POST", null);

        assertNotNull(requestBody);
        assertEquals(0, requestBody.contentLength());
        final Buffer buffer = new Buffer();
        requestBody.writeTo(buffer);
        assertEquals(0, buffer.size());
    }

    @Test
    public void postWithDataPreservesRequestBody() throws IOException {
        final byte[] payload = "ticket-request".getBytes(StandardCharsets.UTF_8);
        final RequestBody requestBody = DownloaderImpl.buildRequestBody("POST", payload);

        assertNotNull(requestBody);
        assertEquals(payload.length, requestBody.contentLength());
        final Buffer buffer = new Buffer();
        requestBody.writeTo(buffer);
        assertArrayEquals(payload, buffer.readByteArray());
    }

    @Test
    public void methodsWithoutDataRemainBodyless() {
        assertNull(DownloaderImpl.buildRequestBody("GET", null));
        assertNull(DownloaderImpl.buildRequestBody("HEAD", null));
        assertNull(DownloaderImpl.buildRequestBody("OPTIONS", null));
    }
}
