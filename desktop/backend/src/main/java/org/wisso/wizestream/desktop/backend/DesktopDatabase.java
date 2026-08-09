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
                    INSERT INTO schema_metadata(key, value) VALUES ('schema_version', '1')
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
        }
    }

    public Connection connection() {
        return connection;
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
