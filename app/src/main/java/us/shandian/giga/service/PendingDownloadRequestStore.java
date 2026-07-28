/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package us.shandian.giga.service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.UUID;

final class PendingDownloadRequestStore {
    private static final String DIRECTORY_NAME = "pending_download_requests";
    private static final String FILE_SUFFIX = ".bin";
    private static final String TEMP_FILE_SUFFIX = ".tmp";

    private PendingDownloadRequestStore() {
    }

    static String write(final File filesDirectory,
                        final Serializable request) throws IOException {
        final File directory = getRequestDirectory(filesDirectory);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Unable to create pending download request directory");
        }

        final String token = UUID.randomUUID().toString();
        final File destination = getRequestFile(filesDirectory, token);
        final File temporary = new File(directory, token + TEMP_FILE_SUFFIX);

        try {
            try (FileOutputStream fileOutput = new FileOutputStream(temporary);
                 ObjectOutputStream objectOutput = new ObjectOutputStream(
                         new BufferedOutputStream(fileOutput))) {
                objectOutput.writeObject(request);
                objectOutput.flush();
                fileOutput.getFD().sync();
            }

            if (!temporary.renameTo(destination)) {
                throw new IOException("Unable to publish pending download request");
            }
            return token;
        } finally {
            if (temporary.exists()) {
                temporary.delete();
            }
        }
    }

    static <T> T take(final File filesDirectory,
                      final String token,
                      final Class<T> type) throws IOException {
        final File requestFile = getRequestFile(filesDirectory, token);
        try {
            try (ObjectInputStream objectInput = new ObjectInputStream(
                    new BufferedInputStream(new FileInputStream(requestFile)))) {
                final Object request = objectInput.readObject();
                if (!type.isInstance(request)) {
                    throw new IOException("Unexpected pending download request type");
                }
                return type.cast(request);
            } catch (final ClassNotFoundException error) {
                throw new IOException("Unable to read pending download request", error);
            }
        } finally {
            delete(filesDirectory, token);
        }
    }

    static void delete(final File filesDirectory, final String token) {
        final File requestFile;
        try {
            requestFile = getRequestFile(filesDirectory, token);
        } catch (final IllegalArgumentException ignored) {
            return;
        }
        if (requestFile.exists()) {
            requestFile.delete();
        }
    }

    static File getRequestFile(final File filesDirectory, final String token) {
        final UUID uuid;
        try {
            uuid = UUID.fromString(token);
        } catch (final IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid pending download request token", error);
        }
        if (!uuid.toString().equals(token)) {
            throw new IllegalArgumentException("Noncanonical pending download request token");
        }
        return new File(getRequestDirectory(filesDirectory), token + FILE_SUFFIX);
    }

    private static File getRequestDirectory(final File filesDirectory) {
        return new File(filesDirectory, DIRECTORY_NAME);
    }
}
