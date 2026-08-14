package org.wisso.wizestream.desktop.backend

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.sql.DriverManager
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopBackupManagerTest {
    private val json = ObjectMapper()

    @Test
    fun `full desktop backup restores libraries and settings transactionally`() {
        val directory = createTempDirectory("wizestream-backup-test")
        try {
            DesktopDatabase(directory).use { database ->
                val library = DesktopLibrary(database.connection())
                val manager = DesktopBackupManager(database.connection(), library)
                val stream = DesktopLibrary.stream(
                    0, "https://www.youtube.com/watch?v=backup", "Backup stream", 300,
                    "VIDEO_STREAM", "WizeStream", null, null
                )
                library.saveSubscription(0, "https://www.youtube.com/@wizestream", "WizeStream", null)
                val playlistId = library.createPlaylist("Study").getValue("id") as String
                library.addPlaylistItem(playlistId, stream)
                library.recordHistory(stream)
                library.recordSearch(0, "backup")
                library.saveLearningNote(null, stream, 42, "Restore this note")

                val backup = directory.resolve("WizeStreamData.zip")
                val settings = json.createObjectNode().put("theme", "dark")
                val exported = manager.exportBackup(backup, settings)
                assertEquals(1, exported["subscriptions"])

                library.deleteSubscription(0, "https://www.youtube.com/@wizestream")
                library.deletePlaylist(playlistId)
                library.clearHistory()
                library.clearSearchHistory()
                database.connection().createStatement().use { it.executeUpdate("DELETE FROM learning_notes") }

                val restored = manager.restoreBackup(backup)
                assertEquals("dark", (restored["settings"] as Map<*, *>)["theme"])
                assertEquals("WizeStream", library.subscriptions().single()["name"])
                assertEquals("Study", library.playlists().single()["name"])
                assertEquals("Backup stream", library.history().single()["title"])
                assertEquals("backup", library.searchHistory().single()["query"])
                assertEquals("Restore this note", library.learningNotes().single()["note"])
            }
        } finally {
            directory.toFile().deleteRecursively()
            assertTrue(Files.notExists(directory))
        }
    }

    @Test
    fun `imports and exports the Android subscription JSON schema`() {
        val directory = createTempDirectory("wizestream-subscription-json-test")
        try {
            DesktopDatabase(directory).use { database ->
                val library = DesktopLibrary(database.connection())
                val manager = DesktopBackupManager(database.connection(), library)
                val input = directory.resolve("subscriptions.json")
                Files.writeString(input, """
                    {"subscriptions":[{"service_id":0,"url":"https://www.youtube.com/@android","name":"Android export"}],"app_version":"1.6.0","app_version_int":1600000}
                """.trimIndent())

                assertEquals(1, manager.importSubscriptions(input)["imported"])
                assertEquals("Android export", library.subscriptions().single()["name"])

                val output = directory.resolve("desktop-subscriptions.json")
                manager.exportSubscriptions(output, "0.6.0-beta.1")
                val exported = json.readTree(output.toFile())
                assertEquals(0, exported.path("subscriptions").first().path("service_id").intValue())
                assertEquals("https://www.youtube.com/@android", exported.path("subscriptions").first().path("url").textValue())
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `imports subscriptions from an Android full backup database`() {
        val directory = createTempDirectory("wizestream-android-backup-test")
        try {
            val androidDatabase = directory.resolve("newpipe.db")
            DriverManager.getConnection("jdbc:sqlite:$androidDatabase").use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate("""
                        CREATE TABLE subscriptions (
                          uid INTEGER PRIMARY KEY, service_id INTEGER NOT NULL, url TEXT,
                          name TEXT, avatar_url TEXT
                        )
                    """.trimIndent())
                    statement.executeUpdate("""
                        INSERT INTO subscriptions(service_id, url, name, avatar_url)
                        VALUES (0, 'https://www.youtube.com/@fullbackup', 'Full backup', NULL)
                    """.trimIndent())
                }
            }
            val zip = directory.resolve("WizeStreamData.zip")
            ZipOutputStream(Files.newOutputStream(zip)).use { output ->
                output.putNextEntry(ZipEntry("backup_manifest.json"))
                output.write("{\"appName\":\"WizeStream\"}".toByteArray())
                output.closeEntry()
                output.putNextEntry(ZipEntry("newpipe.db"))
                Files.copy(androidDatabase, output)
                output.closeEntry()
            }

            DesktopDatabase(directory.resolve("desktop")).use { database ->
                val library = DesktopLibrary(database.connection())
                val manager = DesktopBackupManager(database.connection(), library)
                assertEquals(1, manager.importSubscriptions(zip)["imported"])
                assertEquals("Full backup", library.subscriptions().single()["name"])
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `invalid full backup leaves current data untouched`() {
        val directory = createTempDirectory("wizestream-invalid-backup-test")
        try {
            DesktopDatabase(directory).use { database ->
                val library = DesktopLibrary(database.connection())
                val manager = DesktopBackupManager(database.connection(), library)
                library.saveSubscription(0, "https://www.youtube.com/@safe", "Keep me", null)
                val invalid = directory.resolve("invalid.zip")
                ZipOutputStream(Files.newOutputStream(invalid)).use { output ->
                    output.putNextEntry(ZipEntry("unrelated.txt"))
                    output.write("not a backup".toByteArray())
                    output.closeEntry()
                }
                assertFailsWith<IllegalArgumentException> { manager.restoreBackup(invalid) }
                assertEquals("Keep me", library.subscriptions().single()["name"])
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
