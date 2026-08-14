package org.wisso.wizestream.desktop.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

final class DesktopBackupManager {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DATA_ENTRY = "wizestream-desktop-backup.json";
    private static final String MANIFEST_ENTRY = "backup_manifest.json";
    private static final String ANDROID_DATABASE_ENTRY = "newpipe.db";
    private static final int FORMAT_VERSION = 1;
    private static final long MAX_INPUT_BYTES = 512L * 1024L * 1024L;
    private static final int MAX_JSON_BYTES = 32 * 1024 * 1024;
    private static final int MAX_ANDROID_DATABASE_BYTES = 256 * 1024 * 1024;
    private static final int MAX_RECORDS = 20_000;

    private final Connection connection;
    private final DesktopLibrary library;

    DesktopBackupManager(final Connection connection, final DesktopLibrary library) {
        this.connection = connection;
        this.library = library;
    }

    Map<String, Object> exportBackup(final Path output, final JsonNode settings) throws Exception {
        if (!settings.isObject()) throw new IllegalArgumentException("Desktop settings are missing");
        final ObjectNode root = JSON.createObjectNode();
        root.put("appName", "WizeStream Desktop");
        root.put("backupFormatVersion", FORMAT_VERSION);
        root.put("createdTimestamp", System.currentTimeMillis());
        root.set("settings", settings.deepCopy());
        final ObjectNode data = root.putObject("data");
        data.set("subscriptions", JSON.valueToTree(library.subscriptions()));
        final ArrayNode playlists = data.putArray("playlists");
        for (final Map<String, Object> playlist : library.playlists()) {
            final ObjectNode item = JSON.valueToTree(playlist);
            item.set("items", JSON.valueToTree(library.playlistItems((String) playlist.get("id"))));
            playlists.add(item);
        }
        data.set("history", JSON.valueToTree(library.history()));
        data.set("searchHistory", JSON.valueToTree(library.searchHistory()));
        data.set("learningNotes", JSON.valueToTree(library.learningNotes()));

        final Path target = output.toAbsolutePath().normalize();
        if (target.getParent() != null) Files.createDirectories(target.getParent());
        final Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try (OutputStream file = Files.newOutputStream(temporary);
             ZipOutputStream zip = new ZipOutputStream(file)) {
            zip.putNextEntry(new ZipEntry(MANIFEST_ENTRY));
            final ObjectNode manifest = JSON.createObjectNode();
            manifest.put("appName", "WizeStream Desktop");
            manifest.put("backupFormatVersion", FORMAT_VERSION);
            manifest.put("createdTimestamp", root.path("createdTimestamp").longValue());
            manifest.put("includesDatabase", true);
            manifest.put("includesPreferences", true);
            JSON.writeValue(zip, manifest);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(DATA_ENTRY));
            JSON.writeValue(zip, root);
            zip.closeEntry();
        }
        atomicReplace(temporary, target);
        return summary(root, "exported");
    }

    Map<String, Object> inspectBackup(final Path input) throws Exception {
        return summary(readDesktopBackup(input), "ready");
    }

    Map<String, Object> restoreBackup(final Path input) throws Exception {
        final JsonNode root = readDesktopBackup(input);
        final BackupRecords records = validateBackup(root);
        synchronized (connection) {
            final boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("DELETE FROM playlist_items");
                    statement.executeUpdate("DELETE FROM playlists");
                    statement.executeUpdate("DELETE FROM subscriptions");
                    statement.executeUpdate("DELETE FROM history");
                    statement.executeUpdate("DELETE FROM search_history");
                    statement.executeUpdate("DELETE FROM learning_notes");
                }
                for (final SubscriptionRecord subscription : records.subscriptions()) {
                    library.saveSubscription(subscription.serviceId(), subscription.url(),
                            subscription.name(), subscription.avatarUrl());
                }
                for (final PlaylistRecord playlist : records.playlists()) {
                    final String id = (String) library.createPlaylist(playlist.name()).get("id");
                    for (final DesktopLibrary.StreamInput item : playlist.items()) {
                        library.addPlaylistItem(id, item);
                    }
                }
                for (final StreamRecord item : records.history()) library.recordHistory(item.stream());
                for (final SearchRecord item : records.searchHistory()) {
                    library.recordSearch(item.serviceId(), item.query());
                }
                for (final NoteRecord item : records.learningNotes()) {
                    library.saveLearningNote(null, item.stream(), item.positionSeconds(), item.note());
                }
                connection.commit();
            } catch (final Exception error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
        return summary(root, "restored");
    }

    Map<String, Object> importSubscriptions(final Path input) throws Exception {
        final Path source = checkedInput(input);
        final List<SubscriptionRecord> subscriptions;
        if (looksLikeZip(source)) subscriptions = subscriptionsFromZip(source);
        else subscriptions = subscriptionsFromAndroidJson(Files.readAllBytes(source));
        if (subscriptions.isEmpty()) throw new IllegalArgumentException("No subscriptions were found");
        synchronized (connection) {
            final boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                for (final SubscriptionRecord subscription : subscriptions) {
                    library.saveSubscription(subscription.serviceId(), subscription.url(),
                            subscription.name(), subscription.avatarUrl());
                }
                connection.commit();
            } catch (final Exception error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
        return Map.of("imported", subscriptions.size(), "source", "Android-compatible export");
    }

    Map<String, Object> exportSubscriptions(final Path output, final String appVersion) throws Exception {
        final ObjectNode root = JSON.createObjectNode();
        final ArrayNode items = root.putArray("subscriptions");
        for (final Map<String, Object> subscription : library.subscriptions()) {
            final ObjectNode item = items.addObject();
            item.put("service_id", ((Number) subscription.get("serviceId")).intValue());
            item.put("url", (String) subscription.get("url"));
            item.put("name", (String) subscription.get("name"));
        }
        root.put("app_version", appVersion);
        root.put("app_version_int", 0);
        final Path target = output.toAbsolutePath().normalize();
        if (target.getParent() != null) Files.createDirectories(target.getParent());
        final Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), root);
        atomicReplace(temporary, target);
        return Map.of("exported", items.size());
    }

    private JsonNode readDesktopBackup(final Path input) throws Exception {
        final Path source = checkedInput(input);
        if (!looksLikeZip(source)) throw new IllegalArgumentException("Select a WizeStream Desktop ZIP backup");
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(source))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (DATA_ENTRY.equals(entry.getName())) {
                    final JsonNode root = JSON.readTree(readLimited(zip, MAX_JSON_BYTES));
                    if (!"WizeStream Desktop".equals(root.path("appName").asText())
                            || root.path("backupFormatVersion").asInt(-1) != FORMAT_VERSION
                            || !root.path("settings").isObject()) {
                        throw new IllegalArgumentException("Unsupported WizeStream Desktop backup");
                    }
                    validateBackup(root);
                    return root;
                }
            }
        }
        throw new IllegalArgumentException("This ZIP is not a WizeStream Desktop full backup");
    }

    private List<SubscriptionRecord> subscriptionsFromZip(final Path source) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(source))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (DATA_ENTRY.equals(entry.getName())) {
                    final JsonNode root = JSON.readTree(readLimited(zip, MAX_JSON_BYTES));
                    return parseSubscriptions(root.path("data").path("subscriptions"), false);
                }
                if (entry.getName().toLowerCase().endsWith(".json")) {
                    try {
                        final List<SubscriptionRecord> records = subscriptionsFromAndroidJson(
                                readLimited(zip, MAX_JSON_BYTES));
                        if (!records.isEmpty()) return records;
                    } catch (final IllegalArgumentException ignored) {
                        // Continue to the Android database entry when this is preferences/manifest JSON.
                    }
                }
                if (ANDROID_DATABASE_ENTRY.equals(entry.getName())) {
                    final Path database = Files.createTempFile("wizestream-android-backup-", ".db");
                    try {
                        try (OutputStream output = Files.newOutputStream(database)) {
                            copyLimited(zip, output, MAX_ANDROID_DATABASE_BYTES);
                        }
                        return subscriptionsFromAndroidDatabase(database);
                    } finally {
                        Files.deleteIfExists(database);
                    }
                }
            }
        }
        throw new IllegalArgumentException("The ZIP does not contain Android subscription data");
    }

    private List<SubscriptionRecord> subscriptionsFromAndroidJson(final byte[] bytes) throws Exception {
        if (bytes.length > MAX_JSON_BYTES) throw new IllegalArgumentException("Subscription file is too large");
        final JsonNode root = JSON.readTree(bytes);
        return parseSubscriptions(root.path("subscriptions"), true);
    }

    private List<SubscriptionRecord> subscriptionsFromAndroidDatabase(final Path database) throws Exception {
        final List<SubscriptionRecord> result = new ArrayList<>();
        try (Connection android = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = android.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT service_id, url, name, avatar_url FROM subscriptions")) {
            while (rows.next()) {
                result.add(subscription(rows.getInt(1), rows.getString(2), rows.getString(3),
                        plainHttpUrl(rows.getString(4))));
                if (result.size() > MAX_RECORDS) throw new IllegalArgumentException("Too many subscriptions");
            }
        }
        return List.copyOf(result);
    }

    private BackupRecords validateBackup(final JsonNode root) {
        final JsonNode data = root.path("data");
        final List<SubscriptionRecord> subscriptions = parseSubscriptions(data.path("subscriptions"), false);
        final List<PlaylistRecord> playlists = new ArrayList<>();
        for (final JsonNode playlist : requiredArray(data, "playlists")) {
            final List<DesktopLibrary.StreamInput> items = new ArrayList<>();
            for (final JsonNode item : requiredArray(playlist, "items")) items.add(stream(item));
            playlists.add(new PlaylistRecord(text(playlist, "name", 200), List.copyOf(items)));
        }
        final List<StreamRecord> history = new ArrayList<>();
        for (final JsonNode item : requiredArray(data, "history")) history.add(new StreamRecord(stream(item)));
        final List<SearchRecord> searches = new ArrayList<>();
        for (final JsonNode item : requiredArray(data, "searchHistory")) {
            searches.add(new SearchRecord(nonNegativeInt(item, "serviceId"), text(item, "query", 500)));
        }
        final List<NoteRecord> notes = new ArrayList<>();
        for (final JsonNode item : requiredArray(data, "learningNotes")) {
            final DesktopLibrary.StreamInput stream = stream(item);
            final long position = nonNegativeLong(item, "positionSeconds");
            if (position > Math.max(stream.duration(), 86_400L)) {
                throw new IllegalArgumentException("Invalid learning note position");
            }
            notes.add(new NoteRecord(stream, position, text(item, "note", 10_000)));
        }
        final int total = subscriptions.size() + playlists.size() + history.size() + searches.size() + notes.size();
        if (total > MAX_RECORDS) throw new IllegalArgumentException("Backup contains too many records");
        return new BackupRecords(subscriptions, List.copyOf(playlists), List.copyOf(history),
                List.copyOf(searches), List.copyOf(notes));
    }

    private List<SubscriptionRecord> parseSubscriptions(final JsonNode node, final boolean androidNames) {
        if (!node.isArray()) throw new IllegalArgumentException("Subscriptions are missing");
        final List<SubscriptionRecord> result = new ArrayList<>();
        for (final JsonNode item : node) {
            final String serviceKey = androidNames ? "service_id" : "serviceId";
            result.add(subscription(nonNegativeInt(item, serviceKey), text(item, "url", 2_000),
                    text(item, "name", 500), plainHttpUrl(optionalText(item, "avatarUrl"))));
            if (result.size() > MAX_RECORDS) throw new IllegalArgumentException("Too many subscriptions");
        }
        return List.copyOf(result);
    }

    private SubscriptionRecord subscription(final int serviceId, final String url, final String name,
                                              final String avatarUrl) {
        final String safeName = name == null || name.isBlank()
                ? url : name.substring(0, Math.min(200, name.length()));
        final DesktopLibrary.StreamInput validated = DesktopLibrary.stream(serviceId, url, safeName,
                0, "VIDEO_STREAM", "", null, null);
        return new SubscriptionRecord(serviceId, validated.url(), safeName, avatarUrl);
    }

    private DesktopLibrary.StreamInput stream(final JsonNode item) {
        return DesktopLibrary.stream(nonNegativeInt(item, "serviceId"), text(item, "url", 2_000),
                text(item, "title", 500), nonNegativeLong(item, "duration"),
                text(item, "streamType", 80), optionalText(item, "uploader"),
                plainHttpUrl(optionalText(item, "uploaderUrl")),
                plainHttpUrl(optionalText(item, "thumbnailUrl")));
    }

    private Map<String, Object> summary(final JsonNode root, final String status) {
        final JsonNode data = root.path("data");
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("subscriptions", data.path("subscriptions").size());
        result.put("playlists", data.path("playlists").size());
        result.put("history", data.path("history").size());
        result.put("searchHistory", data.path("searchHistory").size());
        result.put("learningNotes", data.path("learningNotes").size());
        result.put("settings", JSON.convertValue(root.path("settings"), Map.class));
        return result;
    }

    private Path checkedInput(final Path input) throws IOException {
        final Path source = input.toAbsolutePath().normalize();
        if (!Files.isRegularFile(source)) throw new IllegalArgumentException("Backup file was not found");
        if (Files.size(source) > MAX_INPUT_BYTES) throw new IllegalArgumentException("Backup file is too large");
        return source;
    }

    private static boolean looksLikeZip(final Path input) throws IOException {
        try (InputStream stream = Files.newInputStream(input)) {
            return stream.read() == 'P' && stream.read() == 'K';
        }
    }

    private static byte[] readLimited(final InputStream input, final int limit) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        copyLimited(input, output, limit);
        return output.toByteArray();
    }

    private static void copyLimited(final InputStream input, final OutputStream output, final int limit)
            throws IOException {
        final byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > limit) throw new IllegalArgumentException("Backup entry is too large");
            output.write(buffer, 0, count);
        }
    }

    private static void atomicReplace(final Path temporary, final Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (final AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Iterable<JsonNode> requiredArray(final JsonNode parent, final String name) {
        final JsonNode value = parent.path(name);
        if (!value.isArray()) throw new IllegalArgumentException("Backup is missing " + name);
        return value;
    }

    private static int nonNegativeInt(final JsonNode node, final String name) {
        final JsonNode value = node.path(name);
        if (!value.canConvertToInt() || value.intValue() < 0) throw new IllegalArgumentException("Invalid " + name);
        return value.intValue();
    }

    private static long nonNegativeLong(final JsonNode node, final String name) {
        final JsonNode value = node.path(name);
        if (!value.canConvertToLong() || value.longValue() < 0) throw new IllegalArgumentException("Invalid " + name);
        return value.longValue();
    }

    private static String text(final JsonNode node, final String name, final int max) {
        final JsonNode value = node.path(name);
        if (!value.isTextual() || value.textValue().isBlank() || value.textValue().length() > max) {
            throw new IllegalArgumentException("Invalid " + name);
        }
        return value.textValue();
    }

    private static String optionalText(final JsonNode node, final String name) {
        final JsonNode value = node.path(name);
        return value.isTextual() ? value.textValue() : null;
    }

    private static String plainHttpUrl(final String value) {
        if (value == null || value.isBlank()) return null;
        try {
            final URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null ? uri.toString() : null;
        } catch (final IllegalArgumentException ignored) {
            return null;
        }
    }

    private record SubscriptionRecord(int serviceId, String url, String name, String avatarUrl) { }
    private record PlaylistRecord(String name, List<DesktopLibrary.StreamInput> items) { }
    private record StreamRecord(DesktopLibrary.StreamInput stream) { }
    private record SearchRecord(int serviceId, String query) { }
    private record NoteRecord(DesktopLibrary.StreamInput stream, long positionSeconds, String note) { }
    private record BackupRecords(
            List<SubscriptionRecord> subscriptions,
            List<PlaylistRecord> playlists,
            List<StreamRecord> history,
            List<SearchRecord> searchHistory,
            List<NoteRecord> learningNotes
    ) { }
}
