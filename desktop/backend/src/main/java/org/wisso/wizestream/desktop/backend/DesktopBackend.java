package org.wisso.wizestream.desktop.backend;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.schabi.newpipe.sync.DesktopSyncService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DesktopBackend implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ExtractorFacade extractor = new ExtractorFacade();
    private final DesktopDatabase database;
    private final Connection syncConnection;
    private final DesktopLibrary library;
    private final DesktopBackupManager backups;
    private final DesktopSyncService sync;

    private DesktopBackend(final Path dataDirectory) throws Exception {
        database = new DesktopDatabase(dataDirectory);
        library = new DesktopLibrary(database.connection());
        backups = new DesktopBackupManager(database.connection(), library);
        syncConnection = database.openConnection();
        sync = new DesktopSyncService(syncConnection, "WizeStream Desktop");
        sync.start();
    }

    public static void main(final String[] args) throws Exception {
        final Path dataDirectory = dataDirectory(args);
        try (DesktopBackend backend = new DesktopBackend(dataDirectory);
             BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
             PrintWriter output = new PrintWriter(System.out, true, StandardCharsets.UTF_8)) {
            String line;
            while ((line = input.readLine()) != null) output.println(backend.handle(line));
        }
    }

    private String handle(final String line) {
        Integer id = null;
        try {
            final JsonNode request = JSON.readTree(line);
            id = requiredInt(request, "id");
            final String method = requiredText(request, "method");
            final JsonNode params = request.path("params");
            final Object result = switch (method) {
                case "health" -> Map.of("status", "ok", "apiVersion", 1, "extractor", "WizeStreamExtractor");
                case "services.list" -> extractor.services();
                case "search" -> extractor.search(requiredInt(params, "serviceId"), requiredText(params, "query"));
                case "stream.resolve" -> extractor.resolve(requiredText(params, "url"));
                case "library.summary" -> database.summary();
                case "library.subscriptions.list" -> library.subscriptions();
                case "library.subscriptions.save" -> library.saveSubscription(
                        requiredInt(params, "serviceId"), requiredText(params, "url"),
                        requiredText(params, "name"), optionalText(params, "avatarUrl"));
                case "library.subscriptions.delete" -> {
                    library.deleteSubscription(
                            requiredInt(params, "serviceId"), requiredText(params, "url"));
                    yield Map.of("deleted", true);
                }
                case "library.playlists.list" -> library.playlists();
                case "library.playlists.create" -> library.createPlaylist(requiredText(params, "name"));
                case "library.playlists.rename" -> {
                    library.renamePlaylist(requiredText(params, "id"), requiredText(params, "name"));
                    yield Map.of("updated", true);
                }
                case "library.playlists.delete" -> {
                    library.deletePlaylist(requiredText(params, "id"));
                    yield Map.of("deleted", true);
                }
                case "library.playlists.items" -> library.playlistItems(
                        requiredText(params, "playlistId"));
                case "library.playlists.add-item" -> library.addPlaylistItem(
                        requiredText(params, "playlistId"), requiredStream(params));
                case "library.playlists.delete-item" -> {
                    library.deletePlaylistItem(
                            requiredText(params, "playlistId"), requiredText(params, "itemId"));
                    yield Map.of("deleted", true);
                }
                case "library.history.list" -> library.history();
                case "library.history.record" -> library.recordHistory(requiredStream(params));
                case "library.history.delete" -> {
                    library.deleteHistory(requiredText(params, "id"));
                    yield Map.of("deleted", true);
                }
                case "library.history.clear" -> {
                    library.clearHistory();
                    yield Map.of("deleted", true);
                }
                case "library.search-history.list" -> library.searchHistory();
                case "library.search-history.record" -> library.recordSearch(
                        requiredInt(params, "serviceId"), requiredText(params, "query"));
                case "library.search-history.delete" -> {
                    library.deleteSearch(requiredText(params, "id"));
                    yield Map.of("deleted", true);
                }
                case "library.search-history.clear" -> {
                    library.clearSearchHistory();
                    yield Map.of("deleted", true);
                }
                case "library.learning.list" -> library.learningNotes();
                case "library.learning.save" -> library.saveLearningNote(
                        optionalText(params, "id"), requiredStream(params),
                        requiredLong(params, "positionSeconds"), requiredText(params, "note"));
                case "library.learning.delete" -> {
                    library.deleteLearningNote(requiredText(params, "id"));
                    yield Map.of("deleted", true);
                }
                case "library.downloads.record" -> sync.recordCompletedDownload(
                        optionalText(params, "syncId"),
                        requiredText(params, "sourceUrl"),
                        requiredText(params, "displayName"),
                        requiredText(params, "mimeType"),
                        requiredLong(params, "sizeBytes"),
                        requiredLong(params, "completedAt"),
                        requiredText(params, "mediaKind"));
                case "backup.export" -> backups.exportBackup(
                        Path.of(requiredText(params, "path")), params.path("settings"));
                case "backup.inspect" -> backups.inspectBackup(Path.of(requiredText(params, "path")));
                case "backup.restore" -> backups.restoreBackup(Path.of(requiredText(params, "path")));
                case "subscriptions.import" -> backups.importSubscriptions(
                        Path.of(requiredText(params, "path")));
                case "subscriptions.export" -> backups.exportSubscriptions(
                        Path.of(requiredText(params, "path")), requiredText(params, "appVersion"));
                case "sync.status" -> sync.status();
                case "sync.invitation" -> Map.of("pairingCode", sync.createPairingCode());
                case "sync.pair" -> sync.pair(requiredText(params, "pairingCode"));
                case "sync.policy.update" -> sync.updateAutomaticPolicy(
                        requiredBoolean(params, "enabled"),
                        requiredInt(params, "intervalMinutes"),
                        requiredTextList(params, "categories"),
                        requiredTextList(params, "peerIds"));
                case "sync.runs.list" -> sync.recentRuns(optionalInt(params, "limit", 20));
                case "sync.run" -> sync.sync(
                        optionalTextList(params, "categories"),
                        optionalTextList(params, "peerIds"));
                default -> throw new IllegalArgumentException("Unknown backend method");
            };
            final ObjectNode response = JSON.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.put("id", id);
            response.set("result", JSON.valueToTree(result));
            return JSON.writeValueAsString(response);
        } catch (final Exception error) {
            return errorResponse(id, error);
        }
    }

    private String errorResponse(final Integer id, final Exception error) {
        final ObjectNode response = JSON.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (id == null) response.putNull("id"); else response.put("id", id);
        final ObjectNode body = response.putObject("error");
        body.put("code", error instanceof IllegalArgumentException ? -32602 : -32000);
        body.put("message", safeMessage(error));
        try {
            return JSON.writeValueAsString(response);
        } catch (final JsonProcessingException impossible) {
            return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}";
        }
    }

    private static String requiredText(final JsonNode node, final String name) {
        final JsonNode value = node.path(name);
        if (!value.isTextual() || value.textValue().isBlank()) throw new IllegalArgumentException("Missing " + name);
        return value.textValue();
    }

    private static int requiredInt(final JsonNode node, final String name) {
        final JsonNode value = node.path(name);
        if (!value.canConvertToInt()) throw new IllegalArgumentException("Missing " + name);
        return value.intValue();
    }

    private static long requiredLong(final JsonNode node, final String name) {
        final JsonNode value = node.path(name);
        if (!value.canConvertToLong()) throw new IllegalArgumentException("Missing " + name);
        return value.longValue();
    }

    private static boolean requiredBoolean(final JsonNode node, final String name) {
        final JsonNode value = node.path(name);
        if (!value.isBoolean()) throw new IllegalArgumentException("Missing " + name);
        return value.booleanValue();
    }

    private static int optionalInt(final JsonNode node, final String name, final int fallback) {
        final JsonNode value = node.path(name);
        if (value.isMissingNode() || value.isNull()) return fallback;
        if (!value.canConvertToInt()) throw new IllegalArgumentException("Invalid " + name);
        return value.intValue();
    }

    private static String optionalText(final JsonNode node, final String name) {
        final JsonNode value = node.path(name);
        if (value.isMissingNode() || value.isNull()) return null;
        if (!value.isTextual()) throw new IllegalArgumentException("Invalid " + name);
        return value.textValue();
    }

    private static DesktopLibrary.StreamInput requiredStream(final JsonNode node) {
        return DesktopLibrary.stream(
                requiredInt(node, "serviceId"),
                requiredText(node, "url"),
                requiredText(node, "title"),
                requiredLong(node, "duration"),
                requiredText(node, "streamType"),
                optionalText(node, "uploader"),
                optionalText(node, "uploaderUrl"),
                optionalText(node, "thumbnailUrl")
        );
    }

    private static List<String> optionalTextList(final JsonNode node, final String name) {
        final JsonNode value = node.path(name);
        if (value.isMissingNode() || value.isNull()) return null;
        if (!value.isArray() || value.size() > 32) {
            throw new IllegalArgumentException("Invalid " + name);
        }
        final var result = new java.util.ArrayList<String>(value.size());
        value.forEach(item -> {
            if (!item.isTextual() || item.textValue().isBlank() || item.textValue().length() > 80) {
                throw new IllegalArgumentException("Invalid " + name);
            }
            result.add(item.textValue());
        });
        return List.copyOf(result);
    }

    private static List<String> requiredTextList(final JsonNode node, final String name) {
        final List<String> value = optionalTextList(node, name);
        if (value == null) throw new IllegalArgumentException("Missing " + name);
        return value;
    }

    private static String safeMessage(final Exception error) {
        final String value = error.getMessage();
        if (value == null || value.isBlank()) return error.getClass().getSimpleName();
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private static Path dataDirectory(final String[] args) {
        for (int index = 0; index < args.length - 1; index++) {
            if ("--data-dir".equals(args[index])) return Path.of(args[index + 1]).toAbsolutePath().normalize();
        }
        throw new IllegalArgumentException("--data-dir is required");
    }

    @Override
    public void close() throws Exception {
        sync.stop();
        syncConnection.close();
        database.close();
    }
}
