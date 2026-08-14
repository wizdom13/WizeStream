package org.wisso.wizestream.desktop.backend;

import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class DesktopLibrary {
    private final Connection connection;

    DesktopLibrary(final Connection connection) {
        this.connection = connection;
    }

    List<Map<String, Object>> subscriptions() throws SQLException {
        return query("""
                SELECT service_id, url, name, avatar_url, subscriber_count, description,
                       youtube_mode_mask
                FROM subscriptions ORDER BY name COLLATE NOCASE, service_id, url
                """, rows -> row(
                "serviceId", rows.getInt(1),
                "url", rows.getString(2),
                "name", rows.getString(3),
                "avatarUrl", rows.getString(4),
                "subscriberCount", nullableLong(rows, 5),
                "description", rows.getString(6),
                "youtubeModeMask", rows.getInt(7)
        ));
    }

    Map<String, Object> saveSubscription(
            final int serviceId,
            final String url,
            final String name,
            final String avatarUrl
    ) throws SQLException {
        if (serviceId < 0) throw new IllegalArgumentException("Invalid serviceId");
        final String safeUrl = httpUrl(url, "url");
        final String safeName = text(name, "name", 200);
        final String safeAvatar = optionalHttpUrl(avatarUrl, "avatarUrl");
        synchronized (connection) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO subscriptions(service_id, url, name, avatar_url, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(service_id, url) DO UPDATE SET name=excluded.name,
                    avatar_url=excluded.avatar_url
                    """)) {
                statement.setInt(1, serviceId);
                statement.setString(2, safeUrl);
                statement.setString(3, safeName);
                statement.setString(4, safeAvatar);
                statement.setLong(5, System.currentTimeMillis());
                statement.executeUpdate();
            }
        }
        return row("serviceId", serviceId, "url", safeUrl, "name", safeName,
                "avatarUrl", safeAvatar);
    }

    Map<String, Object> updateSubscriptionAvatar(
            final int serviceId,
            final String url,
            final String avatarUrl
    ) throws SQLException {
        if (serviceId < 0) throw new IllegalArgumentException("Invalid serviceId");
        final String safeUrl = httpUrl(url, "url");
        final String safeAvatar = optionalHttpUrl(avatarUrl, "avatarUrl");
        if (safeAvatar == null) throw new IllegalArgumentException("Invalid avatarUrl");
        final int updated;
        synchronized (connection) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE subscriptions SET avatar_url=? WHERE service_id=? AND url=?
                    """)) {
                statement.setString(1, safeAvatar);
                statement.setInt(2, serviceId);
                statement.setString(3, safeUrl);
                updated = statement.executeUpdate();
            }
        }
        if (updated == 0) throw new IllegalArgumentException("Subscription was not found");
        return row("serviceId", serviceId, "url", safeUrl, "avatarUrl", safeAvatar);
    }

    void deleteSubscription(final int serviceId, final String url) throws SQLException {
        if (serviceId < 0) throw new IllegalArgumentException("Invalid serviceId");
        execute("DELETE FROM subscriptions WHERE service_id=? AND url=?", statement -> {
            statement.setInt(1, serviceId);
            statement.setString(2, httpUrl(url, "url"));
        });
    }

    List<Map<String, Object>> playlists() throws SQLException {
        return query("""
                SELECT p.id, p.name, p.thumbnail_url, p.display_index, COUNT(i.position)
                FROM playlists p LEFT JOIN playlist_items i ON i.playlist_id=p.id
                GROUP BY p.id ORDER BY p.display_index, p.created_at, p.id
                """, rows -> row(
                "id", rows.getString(1),
                "name", rows.getString(2),
                "thumbnailUrl", rows.getString(3),
                "displayIndex", rows.getLong(4),
                "itemCount", rows.getLong(5)
        ));
    }

    Map<String, Object> createPlaylist(final String name) throws SQLException {
        final String id = UUID.randomUUID().toString();
        final String safeName = text(name, "name", 200);
        synchronized (connection) {
            long displayIndex = 0;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COALESCE(MAX(display_index), -1) + 1 FROM playlists");
                 ResultSet rows = statement.executeQuery()) {
                if (rows.next()) displayIndex = rows.getLong(1);
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO playlists(id, name, created_at, display_index) VALUES (?, ?, ?, ?)
                    """)) {
                statement.setString(1, id);
                statement.setString(2, safeName);
                statement.setLong(3, System.currentTimeMillis());
                statement.setLong(4, displayIndex);
                statement.executeUpdate();
            }
            return row("id", id, "name", safeName, "displayIndex", displayIndex, "itemCount", 0L);
        }
    }

    void renamePlaylist(final String id, final String name) throws SQLException {
        execute("UPDATE playlists SET name=? WHERE id=?", statement -> {
            statement.setString(1, text(name, "name", 200));
            statement.setString(2, uuid(id, "id"));
        });
    }

    void deletePlaylist(final String id) throws SQLException {
        execute("DELETE FROM playlists WHERE id=?", statement -> statement.setString(1, uuid(id, "id")));
    }

    List<Map<String, Object>> playlistItems(final String playlistId) throws SQLException {
        final String safeId = uuid(playlistId, "playlistId");
        return query("""
                SELECT item_id, position, service_id, url, title, duration, stream_type,
                       uploader, uploader_url, thumbnail_url
                FROM playlist_items WHERE playlist_id=? ORDER BY position
                """, statement -> statement.setString(1, safeId), rows -> streamRow(rows, true));
    }

    Map<String, Object> addPlaylistItem(final String playlistId, final StreamInput stream)
            throws SQLException {
        final String safePlaylistId = uuid(playlistId, "playlistId");
        final String itemId = UUID.randomUUID().toString();
        synchronized (connection) {
            requirePlaylist(safePlaylistId);
            int position = 0;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_items WHERE playlist_id=?")) {
                statement.setString(1, safePlaylistId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next()) position = rows.getInt(1);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO playlist_items(playlist_id, position, service_id, url, title,
                        duration, item_id, stream_type, uploader, uploader_url, thumbnail_url)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, safePlaylistId);
                statement.setInt(2, position);
                statement.setInt(3, stream.serviceId());
                statement.setString(4, stream.url());
                statement.setString(5, stream.title());
                statement.setLong(6, stream.duration());
                statement.setString(7, itemId);
                statement.setString(8, stream.streamType());
                statement.setString(9, stream.uploader());
                statement.setString(10, stream.uploaderUrl());
                statement.setString(11, stream.thumbnailUrl());
                statement.executeUpdate();
            }
            return row("itemId", itemId, "position", position, "serviceId", stream.serviceId(),
                    "url", stream.url(), "title", stream.title(), "duration", stream.duration(),
                    "streamType", stream.streamType(), "uploader", stream.uploader(),
                    "uploaderUrl", stream.uploaderUrl(), "thumbnailUrl", stream.thumbnailUrl());
        }
    }

    void deletePlaylistItem(final String playlistId, final String itemId) throws SQLException {
        final String safePlaylistId = uuid(playlistId, "playlistId");
        final String safeItemId = uuid(itemId, "itemId");
        synchronized (connection) {
            executeLocked("DELETE FROM playlist_items WHERE playlist_id=? AND item_id=?", statement -> {
                statement.setString(1, safePlaylistId);
                statement.setString(2, safeItemId);
            });
            resequencePlaylist(safePlaylistId);
        }
    }

    List<Map<String, Object>> history() throws SQLException {
        return query("""
                SELECT id, service_id, url, title, watched_at, position_seconds, stream_type,
                       duration, uploader, uploader_url, thumbnail_url
                FROM history ORDER BY watched_at DESC, rowid DESC LIMIT 1000
                """, rows -> row(
                "id", rows.getString(1),
                "serviceId", rows.getInt(2),
                "url", rows.getString(3),
                "title", rows.getString(4),
                "watchedAt", rows.getLong(5),
                "positionSeconds", rows.getLong(6),
                "streamType", rows.getString(7),
                "duration", rows.getLong(8),
                "uploader", rows.getString(9),
                "uploaderUrl", rows.getString(10),
                "thumbnailUrl", rows.getString(11)
        ));
    }

    Map<String, Object> recordHistory(final StreamInput stream) throws SQLException {
        final String id = UUID.randomUUID().toString();
        final long now = System.currentTimeMillis();
        synchronized (connection) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO history(service_id, url, title, watched_at, position_seconds,
                        id, stream_type, duration, uploader, uploader_url, thumbnail_url)
                    VALUES (?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setInt(1, stream.serviceId());
                statement.setString(2, stream.url());
                statement.setString(3, stream.title());
                statement.setLong(4, now);
                statement.setString(5, id);
                statement.setString(6, stream.streamType());
                statement.setLong(7, stream.duration());
                statement.setString(8, stream.uploader());
                statement.setString(9, stream.uploaderUrl());
                statement.setString(10, stream.thumbnailUrl());
                statement.executeUpdate();
            }
        }
        return row("id", id, "watchedAt", now);
    }

    void deleteHistory(final String id) throws SQLException {
        execute("DELETE FROM history WHERE id=?", statement -> statement.setString(1, uuid(id, "id")));
    }

    void clearHistory() throws SQLException {
        synchronized (connection) {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM history")) {
                statement.executeUpdate();
            }
        }
    }

    List<Map<String, Object>> searchHistory() throws SQLException {
        return query("""
                SELECT id, service_id, query, searched_at
                FROM search_history ORDER BY searched_at DESC, id DESC LIMIT 1000
                """, rows -> row(
                "id", rows.getString(1),
                "serviceId", rows.getInt(2),
                "query", rows.getString(3),
                "searchedAt", rows.getLong(4)
        ));
    }

    Map<String, Object> recordSearch(final int serviceId, final String query) throws SQLException {
        if (serviceId < 0) throw new IllegalArgumentException("Invalid serviceId");
        final String id = UUID.randomUUID().toString();
        final String safeQuery = text(query, "query", 500);
        final long now = System.currentTimeMillis();
        execute("INSERT INTO search_history(id, service_id, query, searched_at) VALUES (?, ?, ?, ?)",
                statement -> {
                    statement.setString(1, id);
                    statement.setInt(2, serviceId);
                    statement.setString(3, safeQuery);
                    statement.setLong(4, now);
                });
        return row("id", id, "serviceId", serviceId, "query", safeQuery, "searchedAt", now);
    }

    void deleteSearch(final String id) throws SQLException {
        execute("DELETE FROM search_history WHERE id=?",
                statement -> statement.setString(1, uuid(id, "id")));
    }

    void clearSearchHistory() throws SQLException {
        synchronized (connection) {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM search_history")) {
                statement.executeUpdate();
            }
        }
    }

    List<Map<String, Object>> learningNotes() throws SQLException {
        return query("""
                SELECT id, service_id, url, title, position_seconds, note, created_at, updated_at,
                       stream_type, duration, uploader, uploader_url, thumbnail_url
                FROM learning_notes ORDER BY updated_at DESC, id
                """, rows -> row(
                "id", rows.getString(1),
                "serviceId", rows.getInt(2),
                "url", rows.getString(3),
                "title", rows.getString(4),
                "positionSeconds", rows.getLong(5),
                "note", rows.getString(6),
                "createdAt", rows.getLong(7),
                "updatedAt", rows.getLong(8),
                "streamType", rows.getString(9),
                "duration", rows.getLong(10),
                "uploader", rows.getString(11),
                "uploaderUrl", rows.getString(12),
                "thumbnailUrl", rows.getString(13)
        ));
    }

    Map<String, Object> saveLearningNote(
            final String id,
            final StreamInput stream,
            final long positionSeconds,
            final String note
    ) throws SQLException {
        if (positionSeconds < 0 || positionSeconds > Math.max(stream.duration(), 86_400L)) {
            throw new IllegalArgumentException("Invalid positionSeconds");
        }
        final String safeNote = text(note, "note", 10_000);
        final String safeId = id == null || id.isBlank() ? UUID.randomUUID().toString() : uuid(id, "id");
        final long now = System.currentTimeMillis();
        synchronized (connection) {
            long createdAt = now;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT created_at FROM learning_notes WHERE id=?")) {
                statement.setString(1, safeId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next()) createdAt = rows.getLong(1);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO learning_notes(id, service_id, url, position_seconds, note,
                        created_at, updated_at, title, stream_type, duration, uploader,
                        uploader_url, thumbnail_url) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET service_id=excluded.service_id,
                    url=excluded.url, position_seconds=excluded.position_seconds, note=excluded.note,
                    updated_at=excluded.updated_at, title=excluded.title,
                    stream_type=excluded.stream_type, duration=excluded.duration,
                    uploader=excluded.uploader, uploader_url=excluded.uploader_url,
                    thumbnail_url=excluded.thumbnail_url
                    """)) {
                statement.setString(1, safeId);
                statement.setInt(2, stream.serviceId());
                statement.setString(3, stream.url());
                statement.setLong(4, positionSeconds);
                statement.setString(5, safeNote);
                statement.setLong(6, createdAt);
                statement.setLong(7, now);
                statement.setString(8, stream.title());
                statement.setString(9, stream.streamType());
                statement.setLong(10, stream.duration());
                statement.setString(11, stream.uploader());
                statement.setString(12, stream.uploaderUrl());
                statement.setString(13, stream.thumbnailUrl());
                statement.executeUpdate();
            }
            return row("id", safeId, "createdAt", createdAt, "updatedAt", now);
        }
    }

    void deleteLearningNote(final String id) throws SQLException {
        execute("DELETE FROM learning_notes WHERE id=?", statement -> statement.setString(1, uuid(id, "id")));
    }

    static StreamInput stream(
            final int serviceId,
            final String url,
            final String title,
            final long duration,
            final String streamType,
            final String uploader,
            final String uploaderUrl,
            final String thumbnailUrl
    ) {
        if (serviceId < 0) throw new IllegalArgumentException("Invalid serviceId");
        if (duration < 0) throw new IllegalArgumentException("Invalid duration");
        return new StreamInput(
                serviceId,
                httpUrl(url, "url"),
                text(title, "title", 500),
                duration,
                text(streamType, "streamType", 80),
                optionalText(uploader, 500),
                optionalHttpUrl(uploaderUrl, "uploaderUrl"),
                optionalHttpUrl(thumbnailUrl, "thumbnailUrl")
        );
    }

    record StreamInput(
            int serviceId,
            String url,
            String title,
            long duration,
            String streamType,
            String uploader,
            String uploaderUrl,
            String thumbnailUrl
    ) { }

    private void requirePlaylist(final String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM playlists WHERE id=?")) {
            statement.setString(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new IllegalArgumentException("Playlist was not found");
            }
        }
    }

    private void resequencePlaylist(final String playlistId) throws SQLException {
        final List<Long> rowIds = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT rowid FROM playlist_items WHERE playlist_id=? ORDER BY position")) {
            statement.setString(1, playlistId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) rowIds.add(rows.getLong(1));
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE playlist_items SET position=? WHERE rowid=?")) {
            for (int index = 0; index < rowIds.size(); index++) {
                statement.setInt(1, index);
                statement.setLong(2, rowIds.get(index));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static Map<String, Object> streamRow(final ResultSet rows, final boolean hasPosition)
            throws SQLException {
        return row(
                "itemId", rows.getString(1),
                "position", hasPosition ? rows.getInt(2) : null,
                "serviceId", rows.getInt(3),
                "url", rows.getString(4),
                "title", rows.getString(5),
                "duration", rows.getLong(6),
                "streamType", rows.getString(7),
                "uploader", rows.getString(8),
                "uploaderUrl", rows.getString(9),
                "thumbnailUrl", rows.getString(10)
        );
    }

    private List<Map<String, Object>> query(final String sql, final RowMapper mapper)
            throws SQLException {
        return query(sql, ignored -> { }, mapper);
    }

    private List<Map<String, Object>> query(
            final String sql,
            final StatementBinder binder,
            final RowMapper mapper
    ) throws SQLException {
        synchronized (connection) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.bind(statement);
                try (ResultSet rows = statement.executeQuery()) {
                    final List<Map<String, Object>> result = new ArrayList<>();
                    while (rows.next()) result.add(mapper.map(rows));
                    return List.copyOf(result);
                }
            }
        }
    }

    private void execute(final String sql, final StatementBinder binder) throws SQLException {
        synchronized (connection) {
            executeLocked(sql, binder);
        }
    }

    private void executeLocked(final String sql, final StatementBinder binder) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            statement.executeUpdate();
        }
    }

    private static Long nullableLong(final ResultSet rows, final int column) throws SQLException {
        final long value = rows.getLong(column);
        return rows.wasNull() ? null : value;
    }

    private static String text(final String value, final String name, final int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException("Invalid " + name);
        }
        return value.trim();
    }

    private static String optionalText(final String value, final int maximum) {
        if (value == null || value.isBlank()) return "";
        if (value.length() > maximum) throw new IllegalArgumentException("Text is too long");
        return value.trim();
    }

    private static String httpUrl(final String value, final String name) {
        final String safe = text(value, name, 4_096);
        final URI uri;
        try {
            uri = URI.create(safe);
        } catch (final IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid " + name, error);
        }
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null) {
            throw new IllegalArgumentException("Invalid " + name);
        }
        return uri.toString();
    }

    private static String optionalHttpUrl(final String value, final String name) {
        return value == null || value.isBlank() ? null : httpUrl(value, name);
    }

    private static String uuid(final String value, final String name) {
        try {
            final String parsed = UUID.fromString(value).toString();
            if (!parsed.equals(value)) throw new IllegalArgumentException("Invalid " + name);
            return parsed;
        } catch (final RuntimeException error) {
            throw new IllegalArgumentException("Invalid " + name, error);
        }
    }

    private static Map<String, Object> row(final Object... values) {
        final Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            if (values[index + 1] != null) result.put((String) values[index], values[index + 1]);
        }
        return result;
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    @FunctionalInterface
    private interface RowMapper {
        Map<String, Object> map(ResultSet rows) throws SQLException;
    }
}
