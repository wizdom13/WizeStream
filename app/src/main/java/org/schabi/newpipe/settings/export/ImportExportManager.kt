package org.schabi.newpipe.settings.export

import android.content.SharedPreferences
import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import com.grack.nanojson.JsonWriter
import java.io.DataInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import org.schabi.newpipe.database.Migrations
import org.schabi.newpipe.streams.io.SharpInputStream
import org.schabi.newpipe.streams.io.SharpOutputStream
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.ZipHelper

class ImportExportManager(private val fileLocator: BackupFileLocator) {
    companion object {
        const val TAG = "ImportExportManager"
        private const val MANIFEST_FORMAT_VERSION = 1
        private const val SQLITE_HEADER_LENGTH = 16
        private const val SQLITE_USER_VERSION_OFFSET = 60
        private const val SQLITE_HEADER_TO_VERSION_LENGTH =
            SQLITE_USER_VERSION_OFFSET - SQLITE_HEADER_LENGTH
        private const val SQLITE_MAGIC = "SQLite format 3\u0000"
    }

    data class BackupContents(
        val hasDatabase: Boolean,
        val hasJsonPreferences: Boolean,
        val hasSerializedPreferences: Boolean,
        val hasManifest: Boolean,
        val manifest: String?
    ) {
        val hasRecognizableBackupData: Boolean
            get() = hasDatabase || hasJsonPreferences || hasSerializedPreferences
    }

    /**
     * Exports given [SharedPreferences] to the file in given outputPath.
     * It also creates the file.
     */
    @Throws(Exception::class)
    fun exportDatabase(preferences: SharedPreferences, file: StoredFileHelper) {
        // truncate the file before writing to it, otherwise if the new content is smaller than the
        // previous file size, the file will retain part of the previous content and be corrupted
        ZipOutputStream(SharpOutputStream(file.openAndTruncateStream()).buffered()).use { outZip ->
            // add the database
            val name = BackupFileLocator.FILE_NAME_DB
            ZipHelper.addFileToZip(outZip, name, fileLocator.db)

            // add the JSON preferences; legacy serialized preferences are still supported when
            // importing old backups, but new backups avoid writing that vulnerable format.
            ZipHelper.addFileToZip(
                outZip,
                BackupFileLocator.FILE_NAME_JSON_PREFS
            ) { byteOutput ->
                JsonWriter
                    .indent("")
                    .on(byteOutput)
                    .`object`(preferences.all)
                    .done()
            }

            ZipHelper.addFileToZip(
                outZip,
                BackupFileLocator.FILE_NAME_MANIFEST
            ) { byteOutput ->
                JsonWriter
                    .indent("")
                    .on(byteOutput)
                    .`object`()
                    .value("appName", "WizeStream")
                    .value("backupFormatVersion", MANIFEST_FORMAT_VERSION)
                    .value("createdTimestamp", System.currentTimeMillis())
                    .value("includesDatabase", true)
                    .value("includesPreferences", true)
                    .value(
                        "includesSponsorBlockSettings",
                        preferences.all.keys.any {
                            it.startsWith("sponsor_block_")
                        }
                    )
                    .end()
                    .done()
            }
        }
    }

    /**
     * Tries to create database directory if it does not exist.
     */
    @Throws(IOException::class)
    fun ensureDbDirectoryExists() {
        fileLocator.db.createParentDirectories()
    }

    /**
     * Extracts the database from the given file to the app's database directory.
     * The current app's database will be overwritten.
     * @param file the .zip file to extract the database from
     * @return true if the database was successfully extracted, false otherwise
     */
    fun extractDb(file: StoredFileHelper): Boolean {
        val name = BackupFileLocator.FILE_NAME_DB
        val importedDb = fileLocator.db.resolveSibling("${fileLocator.db.fileName}.import")
        importedDb.deleteIfExists()

        try {
            if (!ZipHelper.extractFileFromZip(file, name, importedDb)) {
                return false
            }
            val databaseVersion = readSqliteUserVersion(importedDb)
            if (databaseVersion !in Migrations.DB_VER_1..Migrations.DB_VER_23) {
                return false
            }

            Files.move(importedDb, fileLocator.db, StandardCopyOption.REPLACE_EXISTING)
            fileLocator.dbJournal.deleteIfExists()
            fileLocator.dbWal.deleteIfExists()
            fileLocator.dbShm.deleteIfExists()
            return true
        } finally {
            importedDb.deleteIfExists()
        }
    }

    private fun readSqliteUserVersion(database: java.nio.file.Path): Int? {
        return try {
            DataInputStream(Files.newInputStream(database).buffered()).use { input ->
                val header = ByteArray(SQLITE_HEADER_LENGTH)
                input.readFully(header)
                if (header.toString(Charsets.US_ASCII) != SQLITE_MAGIC) {
                    return null
                }
                if (input.skipBytes(SQLITE_HEADER_TO_VERSION_LENGTH)
                    != SQLITE_HEADER_TO_VERSION_LENGTH
                ) {
                    return null
                }
                input.readInt()
            }
        } catch (error: IOException) {
            null
        }
    }

    @Deprecated(
        "Serializing preferences with Java's ObjectOutputStream is vulnerable to injections",
        replaceWith = ReplaceWith("exportHasJsonPrefs")
    )
    fun exportHasSerializedPrefs(zipFile: StoredFileHelper): Boolean {
        return ZipHelper.zipContainsFile(zipFile, BackupFileLocator.FILE_NAME_SERIALIZED_PREFS)
    }

    fun exportHasJsonPrefs(zipFile: StoredFileHelper): Boolean {
        return ZipHelper.zipContainsFile(zipFile, BackupFileLocator.FILE_NAME_JSON_PREFS)
    }

    @Throws(IOException::class)
    fun inspectBackup(zipFile: StoredFileHelper): BackupContents {
        var hasDatabase = false
        var hasJsonPreferences = false
        var hasSerializedPreferences = false
        var hasManifest = false
        var manifest: String? = null

        ZipInputStream(SharpInputStream(zipFile.stream).buffered()).use { inZip ->
            var entry = inZip.nextEntry
            while (entry != null) {
                when (entry.name) {
                    BackupFileLocator.FILE_NAME_DB -> hasDatabase = true

                    BackupFileLocator.FILE_NAME_JSON_PREFS -> hasJsonPreferences = true

                    BackupFileLocator.FILE_NAME_SERIALIZED_PREFS -> hasSerializedPreferences = true

                    BackupFileLocator.FILE_NAME_MANIFEST -> {
                        hasManifest = true
                        manifest = inZip.readBytes().decodeToString()
                    }
                }
                entry = inZip.nextEntry
            }
        }

        return BackupContents(
            hasDatabase,
            hasJsonPreferences,
            hasSerializedPreferences,
            hasManifest,
            manifest
        )
    }

    /**
     * Remove all shared preferences from the app and load the preferences supplied to the manager.
     */
    @Deprecated(
        "Serializing preferences with Java's ObjectOutputStream is vulnerable to injections",
        replaceWith = ReplaceWith("loadJsonPrefs")
    )
    @Throws(IOException::class, ClassNotFoundException::class)
    fun loadSerializedPrefs(zipFile: StoredFileHelper, preferences: SharedPreferences) {
        ZipHelper.extractFileFromZip(zipFile, BackupFileLocator.FILE_NAME_SERIALIZED_PREFS) {
            PreferencesObjectInputStream(it).use { input ->
                @Suppress("UNCHECKED_CAST")
                val entries = input.readObject() as Map<String, *>

                val editor = preferences.edit()
                editor.clear()

                for ((key, value) in entries) {
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)

                        is Float -> editor.putFloat(key, value)

                        is Int -> editor.putInt(key, value)

                        is Long -> editor.putLong(key, value)

                        is String -> editor.putString(key, value)

                        is Set<*> -> {
                            // There are currently only Sets with type String possible
                            @Suppress("UNCHECKED_CAST")
                            editor.putStringSet(key, value as Set<String>?)
                        }
                    }
                }

                if (!editor.commit()) {
                    throw IOException("Unable to commit loadSerializedPrefs")
                }
            }
        }.let { fileExists ->
            if (!fileExists) {
                throw FileNotFoundException(BackupFileLocator.FILE_NAME_SERIALIZED_PREFS)
            }
        }
    }

    /**
     * Remove all shared preferences from the app and load the preferences supplied to the manager.
     */
    @Throws(IOException::class, JsonParserException::class)
    fun loadJsonPrefs(zipFile: StoredFileHelper, preferences: SharedPreferences) {
        ZipHelper.extractFileFromZip(zipFile, BackupFileLocator.FILE_NAME_JSON_PREFS) {
            val jsonObject = JsonParser.`object`().from(it)
            val entries = mutableMapOf<String, Any?>()

            for ((key, value) in jsonObject) {
                when (value) {
                    is Boolean, is Float, is Int, is Long, is String -> entries[key] = value
                    is JsonArray -> entries[key] = value.mapNotNull { e -> e as? String }.toSet()
                }
            }

            val editor = preferences.edit()
            editor.clear()

            for ((key, value) in entries) {
                @Suppress("UNCHECKED_CAST")
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is String -> editor.putString(key, value)
                    is Set<*> -> editor.putStringSet(key, value as Set<String>?)
                }
            }

            if (!editor.commit()) {
                throw IOException("Unable to commit loadJsonPrefs")
            }
        }.let { fileExists ->
            if (!fileExists) {
                throw FileNotFoundException(BackupFileLocator.FILE_NAME_JSON_PREFS)
            }
        }
    }
}
