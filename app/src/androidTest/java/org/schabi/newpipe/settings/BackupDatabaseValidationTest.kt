package org.schabi.newpipe.settings

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.Migrations

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
    fun sameVersionForeignDatabaseFailsBeforeReplacingLiveData() {
        context.deleteDatabase(FOREIGN_DATABASE)
        SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(FOREIGN_DATABASE),
            null
        ).use { database ->
            database.execSQL("CREATE TABLE foreign_data (id INTEGER PRIMARY KEY)")
            database.version = Migrations.DB_VER_23
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
