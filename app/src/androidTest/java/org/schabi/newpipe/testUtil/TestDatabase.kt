package org.schabi.newpipe.testUtil

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.Executor
import org.junit.Assert.assertSame
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.AppDatabase

/** Installs a fresh database around setup, the test and cleanup, then restores the previous one. */
class TestDatabase : TestRule {
    lateinit var database: AppDatabase
        private set

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            var previousDatabase: Any? = null
            try {
                synchronized(NewPipeDatabase::class.java) {
                    previousDatabase = databaseField.get(null)
                    database = createReplacingNewPipeDatabase()
                }
                base.evaluate()
            } finally {
                // Detach the fixture before closing it so later tests never inherit its closed DB.
                synchronized(NewPipeDatabase::class.java) {
                    databaseField.set(null, previousDatabase)
                }
                if (::database.isInitialized) database.close()
            }
        }
    }

    companion object {
        private val databaseField = NewPipeDatabase::class.java.getDeclaredField("databaseInstance").apply {
            isAccessible = true
        }

        @JvmOverloads
        fun createReplacingNewPipeDatabase(queryExecutor: Executor? = null): AppDatabase {
            val database = Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase::class.java
            )
                .allowMainThreadQueries()
                .apply {
                    queryExecutor?.let {
                        setQueryExecutor(it)
                        setTransactionExecutor(it)
                    }
                }
                .build()

            // Serialize with lazy initialization so an in-flight background open cannot
            // overwrite the fixture after its identity has been checked.
            synchronized(NewPipeDatabase::class.java) {
                databaseField.set(null, database)
                assertSame(
                    "Mocking database failed!",
                    database,
                    NewPipeDatabase.getInstance(ApplicationProvider.getApplicationContext())
                )
            }

            return database
        }
    }
}
