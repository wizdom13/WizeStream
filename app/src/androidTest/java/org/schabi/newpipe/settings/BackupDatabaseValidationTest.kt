package org.schabi.newpipe.settings

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.provider.MediaStore
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.Migrations
import org.schabi.newpipe.settings.export.BackupFileLocator
import org.schabi.newpipe.settings.export.ImportExportManager
import org.schabi.newpipe.streams.io.StoredFileHelper
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class BackupDatabaseValidationTest {
    companion object {
        private const val VALID_DATABASE = "valid-backup-import.db"
        private const val FOREIGN_DATABASE = "foreign-backup-import.db"
    }

    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun compatibleDatabasePassesRoomValidation() {
        context.deleteDatabase(VALID_DATABASE)
        migrationHelper.createDatabase(VALID_DATABASE, Migrations.DB_VER_23).close()

        try {
            NewPipeDatabase.validateImportDatabase(context, VALID_DATABASE)
        } finally {
            context.deleteDatabase(VALID_DATABASE)
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun contentUriBackupStagesAndValidatesThroughStoredFileHelper() {
        val sourceDatabase = "content-uri-backup-source.db"
        context.deleteDatabase(sourceDatabase)
        migrationHelper.createDatabase(sourceDatabase, Migrations.DB_VER_CURRENT).close()

        val resolver = context.contentResolver
        val uri = resolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "wizestream-content-backup.zip")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            }
        ) ?: error("Could not create MediaStore backup fixture")

        var staged: java.nio.file.Path? = null
        try {
            resolver.openOutputStream(uri, "w")!!.use { raw ->
                ZipOutputStream(raw).use { output ->
                    output.putNextEntry(ZipEntry(BackupFileLocator.FILE_NAME_DB))
                    context.getDatabasePath(sourceDatabase).inputStream().use { input ->
                        input.copyTo(output)
                    }
                    output.closeEntry()
                }
            }

            val storedFile = StoredFileHelper(context, uri, "application/zip")
            assertFalse(storedFile.isDirect)

            val manager = ImportExportManager(BackupFileLocator(context))
            val stagedDatabase = manager.stageDb(storedFile)
            staged = stagedDatabase
            assertTrue(java.nio.file.Files.size(stagedDatabase) > 0)
            NewPipeDatabase.validateImportDatabase(
                context,
                stagedDatabase.fileName.toString()
            )
        } finally {
            staged?.let { ImportExportManager(BackupFileLocator(context)).discardStagedDb(it) }
            resolver.delete(uri, null, null)
            context.deleteDatabase(sourceDatabase)
        }
    }

    @Test
    fun sameVersionForeignDatabaseFailsBeforeReplacingLiveData() {
        context.deleteDatabase(FOREIGN_DATABASE)
        SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(FOREIGN_DATABASE),
            null
        ).use { database ->
            database.execSQL("CREATE TABLE foreign_data (id INTEGER PRIMARY KEY)")
            database.version = Migrations.DB_VER_CURRENT
        }

        try {
            assertThrows(IllegalStateException::class.java) {
                NewPipeDatabase.validateImportDatabase(context, FOREIGN_DATABASE)
            }
        } finally {
            context.deleteDatabase(FOREIGN_DATABASE)
        }
    }
}
