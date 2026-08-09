package org.wisso.wizestream.desktop.backend;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DesktopDatabase implements AutoCloseable {
    private final Connection connection;

    DesktopDatabase(final Path dataDirectory) throws Exception {
        Files.createDirectories(dataDirectory);
        connection = DriverManager.getConnection("jdbc:sqlite:" + dataDirectory.resolve("wizestream-desktop.db"));
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS schema_metadata (
                      key TEXT PRIMARY KEY NOT NULL,
                      value TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO schema_metadata(key, value) VALUES ('schema_version', '2')
                    ON CONFLICT(key) DO UPDATE SET value = excluded.value
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS subscriptions (
                      service_id INTEGER NOT NULL, url TEXT NOT NULL, name TEXT NOT NULL,
                      avatar_url TEXT, created_at INTEGER NOT NULL,
                      PRIMARY KEY(service_id, url)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS playlists (
                      id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, created_at INTEGER NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS playlist_items (
                      playlist_id TEXT NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
                      position INTEGER NOT NULL, service_id INTEGER NOT NULL, url TEXT NOT NULL,
                      title TEXT NOT NULL, duration INTEGER NOT NULL DEFAULT 0,
                      PRIMARY KEY(playlist_id, position)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS history (
                      service_id INTEGER NOT NULL, url TEXT NOT NULL, title TEXT NOT NULL,
                      watched_at INTEGER NOT NULL, position_seconds INTEGER NOT NULL DEFAULT 0,
                      PRIMARY KEY(service_id, url, watched_at)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS learning_notes (
                      id TEXT PRIMARY KEY NOT NULL, service_id INTEGER NOT NULL, url TEXT NOT NULL,
                      position_seconds INTEGER NOT NULL, note TEXT NOT NULL,
                      created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_state (
                      key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS trusted_peers (
                      peer_id TEXT PRIMARY KEY NOT NULL, public_key TEXT NOT NULL,
                      device_name TEXT NOT NULL, addresses_json TEXT NOT NULL,
                      paired_at INTEGER NOT NULL, last_sync_at INTEGER, last_sync_error TEXT
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS remote_playlists (
                      record_id TEXT PRIMARY KEY NOT NULL, service_id INTEGER NOT NULL,
                      url TEXT NOT NULL, name TEXT, thumbnail_url TEXT, uploader TEXT,
                      display_index INTEGER NOT NULL DEFAULT -1, stream_count INTEGER,
                      UNIQUE(service_id, url)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS search_history (
                      id TEXT PRIMARY KEY NOT NULL, service_id INTEGER NOT NULL,
                      query TEXT NOT NULL, searched_at INTEGER NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS playback_state (
                      service_id INTEGER NOT NULL, url TEXT NOT NULL,
                      position_millis INTEGER NOT NULL, updated_at INTEGER NOT NULL,
                      PRIMARY KEY(service_id, url)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS portable_records (
                      category TEXT NOT NULL, record_id TEXT NOT NULL, record_type TEXT NOT NULL,
                      parent_record_id TEXT, payload_json TEXT NOT NULL,
                      PRIMARY KEY(category, record_id)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_changes (
                      namespace TEXT NOT NULL, origin_peer_id TEXT NOT NULL,
                      origin_revision INTEGER NOT NULL, lamport_version INTEGER NOT NULL,
                      record_id TEXT NOT NULL, record_type TEXT NOT NULL,
                      parent_record_id TEXT, change_type TEXT NOT NULL, payload_json TEXT,
                      PRIMARY KEY(namespace, origin_peer_id, origin_revision)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS sync_changes_pending
                    ON sync_changes(namespace, origin_peer_id, origin_revision)
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_records (
                      namespace TEXT NOT NULL, record_id TEXT NOT NULL, record_type TEXT NOT NULL,
                      parent_record_id TEXT, lamport_version INTEGER NOT NULL,
                      origin_peer_id TEXT NOT NULL, origin_revision INTEGER NOT NULL,
                      is_deleted INTEGER NOT NULL, payload_json TEXT,
                      PRIMARY KEY(namespace, record_id)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_origins (
                      namespace TEXT NOT NULL, origin_peer_id TEXT NOT NULL,
                      contiguous_revision INTEGER NOT NULL,
                      PRIMARY KEY(namespace, origin_peer_id)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_peers (
                      namespace TEXT NOT NULL, peer_id TEXT NOT NULL, origin_peer_id TEXT NOT NULL,
                      acknowledged_revision INTEGER NOT NULL,
                      PRIMARY KEY(namespace, peer_id, origin_peer_id)
                    )
                    """);
        }
        ensureColumn("subscriptions", "subscriber_count", "INTEGER");
        ensureColumn("subscriptions", "description", "TEXT");
        ensureColumn("subscriptions", "youtube_mode_mask", "INTEGER NOT NULL DEFAULT 1");
        ensureColumn("playlists", "is_thumbnail_permanent", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("playlists", "thumbnail_service_id", "INTEGER");
        ensureColumn("playlists", "thumbnail_url", "TEXT");
        ensureColumn("playlists", "display_index", "INTEGER NOT NULL DEFAULT -1");
        ensureColumn("playlist_items", "item_id", "TEXT");
        ensureColumn("playlist_items", "stream_type", "TEXT NOT NULL DEFAULT 'VIDEO_STREAM'");
        ensureColumn("playlist_items", "uploader", "TEXT NOT NULL DEFAULT ''");
        ensureColumn("playlist_items", "uploader_url", "TEXT");
        ensureColumn("playlist_items", "thumbnail_url", "TEXT");
        ensureColumn("history", "id", "TEXT");
        ensureColumn("history", "stream_type", "TEXT NOT NULL DEFAULT 'VIDEO_STREAM'");
        ensureColumn("history", "duration", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("history", "uploader", "TEXT NOT NULL DEFAULT ''");
        ensureColumn("history", "uploader_url", "TEXT");
        ensureColumn("history", "thumbnail_url", "TEXT");
        ensureColumn("learning_notes", "title", "TEXT NOT NULL DEFAULT ''");
        ensureColumn("learning_notes", "stream_type", "TEXT NOT NULL DEFAULT 'VIDEO_STREAM'");
        ensureColumn("learning_notes", "duration", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("learning_notes", "uploader", "TEXT NOT NULL DEFAULT ''");
        ensureColumn("learning_notes", "uploader_url", "TEXT");
        ensureColumn("learning_notes", "thumbnail_url", "TEXT");
    }

    public Connection connection() {
        return connection;
    }

    private void ensureColumn(final String table, final String column, final String declaration)
            throws SQLException {
        try (var statement = connection.prepareStatement("PRAGMA table_info(" + table + ")");
             var rows = statement.executeQuery()) {
            while (rows.next()) {
                if (column.equalsIgnoreCase(rows.getString("name"))) return;
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + declaration);
        }
    }

    Map<String, Long> summary() throws SQLException {
        final Map<String, Long> result = new LinkedHashMap<>();
        for (final String table : new String[]{"subscriptions", "playlists", "history", "learning_notes"}) {
            try (Statement statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                result.put(table, rows.next() ? rows.getLong(1) : 0L);
            }
        }
        return result;
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
