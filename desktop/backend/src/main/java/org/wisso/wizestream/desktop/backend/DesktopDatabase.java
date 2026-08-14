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
    private static final int SCHEMA_VERSION = 3;
    private final String jdbcUrl;
    private final Connection connection;

    DesktopDatabase(final Path dataDirectory) throws Exception {
        Files.createDirectories(dataDirectory);
        jdbcUrl = "jdbc:sqlite:" + dataDirectory.resolve("wizestream-desktop.db");
        connection = openConnection();
        connection.setAutoCommit(false);
        try {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS schema_metadata (
                      key TEXT PRIMARY KEY NOT NULL,
                      value TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    INSERT OR IGNORE INTO schema_metadata(key, value) VALUES ('schema_version', '2')
                    """);
            final int currentVersion = schemaVersion();
            if (currentVersion < 1 || currentVersion > SCHEMA_VERSION) {
                throw new SQLException("Unsupported desktop database schema version: " + currentVersion);
            }
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
        createAutomaticSyncSchema();
        ensureColumn("sync_peer_retry_state", "failed_categories_json", "TEXT NOT NULL DEFAULT '[]'");
        try (var statement = connection.prepareStatement(
                "UPDATE schema_metadata SET value=? WHERE key='schema_version'")) {
            statement.setString(1, Integer.toString(SCHEMA_VERSION));
            statement.executeUpdate();
        }
        connection.commit();
        } catch (final Exception error) {
            connection.rollback();
            throw error;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public Connection connection() {
        return connection;
    }

    public Connection openConnection() throws SQLException {
        final Connection opened = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = opened.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
        }
        return opened;
    }

    private void createAutomaticSyncSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_policy (
                      id INTEGER PRIMARY KEY CHECK(id=1), enabled INTEGER NOT NULL DEFAULT 0,
                      interval_minutes INTEGER NOT NULL DEFAULT 60,
                      categories_json TEXT NOT NULL, peer_ids_json TEXT NOT NULL,
                      updated_at INTEGER NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    INSERT OR IGNORE INTO sync_policy(
                      id, enabled, interval_minutes, categories_json, peer_ids_json, updated_at
                    ) VALUES (1, 0, 60,
                      '["subscriptions","playlists","watchHistory","learningNotes","feedGroups","homeTabs","channelProfiles","filters","settings","completedDownloads"]',
                      '[]', 0)
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_schedule_state (
                      id INTEGER PRIMARY KEY CHECK(id=1), next_run_at INTEGER,
                      next_wake_at INTEGER, last_attempt_at INTEGER, last_outcome TEXT
                    )
                    """);
            statement.executeUpdate("INSERT OR IGNORE INTO sync_schedule_state(id) VALUES (1)");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_peer_retry_state (
                      peer_id TEXT PRIMARY KEY NOT NULL REFERENCES trusted_peers(peer_id) ON DELETE CASCADE,
                      failure_count INTEGER NOT NULL DEFAULT 0, next_retry_at INTEGER,
                      last_attempt_at INTEGER, last_outcome TEXT,
                      failed_categories_json TEXT NOT NULL DEFAULT '[]'
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_run_log (
                      run_id TEXT PRIMARY KEY NOT NULL, trigger TEXT NOT NULL,
                      started_at INTEGER NOT NULL, completed_at INTEGER,
                      outcome TEXT NOT NULL, requested_categories_json TEXT NOT NULL,
                      requested_peer_ids_json TEXT NOT NULL,
                      succeeded INTEGER NOT NULL DEFAULT 0, failed INTEGER NOT NULL DEFAULT 0,
                      error TEXT
                    )
                    """);
            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS sync_run_log_started_at
                    ON sync_run_log(started_at DESC)
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_peer_run_log (
                      run_id TEXT NOT NULL REFERENCES sync_run_log(run_id) ON DELETE CASCADE,
                      peer_id TEXT NOT NULL, device_name TEXT NOT NULL, outcome TEXT NOT NULL,
                      error TEXT, details_json TEXT NOT NULL,
                      PRIMARY KEY(run_id, peer_id)
                    )
                    """);
        }
    }

    private int schemaVersion() throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT value FROM schema_metadata WHERE key='schema_version'");
             var rows = statement.executeQuery()) {
            if (!rows.next()) throw new SQLException("Desktop database schema version is missing");
            try {
                return Integer.parseInt(rows.getString(1));
            } catch (final NumberFormatException error) {
                throw new SQLException("Desktop database schema version is invalid", error);
            }
        }
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
