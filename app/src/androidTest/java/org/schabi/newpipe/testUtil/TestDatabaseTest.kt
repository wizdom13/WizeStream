package org.schabi.newpipe.testUtil

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType

class TestDatabaseTest {
    @get:Rule
    val originalDatabase = TestDatabase()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun consecutiveFixturesRestoreThePreviousDatabaseWithoutSharingRows() {
        insertStream(originalDatabase.database, "Original")
        repeat(2) { index ->
            val fixture = TestDatabase()
            fixture.apply(
                object : Statement() {
                    override fun evaluate() {
                        assertSame(fixture.database, NewPipeDatabase.getInstance(context))
                        assertNull(fixture.database.streamDAO().getStreamDirect(0, URL))
                        insertStream(fixture.database, "Fixture $index")
                        assertEquals("Fixture $index", fixture.database.streamDAO().getStreamDirect(0, URL)?.title)
                    }
                },
                Description.EMPTY
            ).evaluate()

            assertFalse(fixture.database.isOpen)
            assertOriginalDatabaseRestored()
        }
    }

    @Test
    fun failingFixturesAlsoRestoreThePreviousDatabase() {
        insertStream(originalDatabase.database, "Original")
        val fixture = TestDatabase()
        val failure = AssertionError("fixture failure")
        try {
            fixture.apply(
                object : Statement() {
                    override fun evaluate() {
                        insertStream(fixture.database, "Failed fixture")
                        throw failure
                    }
                },
                Description.EMPTY
            ).evaluate()
            fail("Expected the fixture failure")
        } catch (actual: AssertionError) {
            assertSame(failure, actual)
        }

        assertFalse(fixture.database.isOpen)
        assertOriginalDatabaseRestored()
    }

    private fun assertOriginalDatabaseRestored() {
        assertSame(originalDatabase.database, NewPipeDatabase.getInstance(context))
        assertEquals("Original", NewPipeDatabase.getInstance(context).streamDAO().getStreamDirect(0, URL)?.title)
    }

    private fun insertStream(database: AppDatabase, title: String) {
        database.streamDAO().insert(
            StreamEntity(serviceId = 0, url = URL, title = title, streamType = StreamType.VIDEO_STREAM, duration = 60, uploader = "Channel")
        )
    }

    companion object {
        private const val URL = "https://example.com/fixture-video"
    }
}
