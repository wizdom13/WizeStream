/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package us.shandian.giga.postprocessing

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceInputStream
import androidx.media3.datasource.DataSpec
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.download.hls.HlsDownloadCache
import org.schabi.newpipe.download.hls.HlsDownloadPlan
import org.schabi.newpipe.streams.io.SharpStream
import org.schabi.newpipe.streams.io.StoredFileHelper
import us.shandian.giga.get.DownloadMission
import us.shandian.giga.get.HlsDownloadMission
import us.shandian.giga.io.FileStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], application = Application::class)
class HlsToMp4PostprocessingTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun cancelAndDeleteRemovesFileRecreatedByAnAlreadyPendingDestinationOpen() = Fixture().use { fixture ->
        val opening = CountDownLatch(1)
        val permitOpen = CountDownLatch(1)
        val recreated = AtomicBoolean()
        val failure = AtomicReference<Throwable>()
        fixture.storage.beforeOpen = {
            opening.countDown()
            awaitIgnoringInterrupt(permitOpen)
        }
        fixture.storage.afterOpen = { recreated.set(fixture.destination.exists()) }
        val worker = Thread({
            try {
                HlsToMp4Postprocessing.copyOutput(fixture.mission, fixture.output)
            } catch (error: Throwable) {
                failure.set(error)
            }
        }, "hls-copy-cancellation-fixture")
        fixture.mission.threads = arrayOf(worker)
        fixture.mission.psState = 1
        worker.start()
        try {
            await(opening)
            assertTrue(fixture.mission.cancel(true))
            assertFalse(fixture.destination.exists())
        } finally {
            permitOpen.countDown()
            worker.join(5_000)
        }

        assertFalse("Copy worker did not stop", worker.isAlive)
        assertTrue("The fixture must exercise reopening after deletion", recreated.get())
        assertTrue(failure.get() is InterruptedIOException)
        assertTrue(fixture.storage.lastStream!!.isClosed)
        assertFalse("Cancelled output was recreated", fixture.destination.exists())
    }

    @Test
    fun failedFinalCopyPreservesCompletedInputAndCanBeRetriedWithoutDownloadingAgain() = Fixture().use { fixture ->
        val plan = fixture.mission.hlsPlan
        fixture.storage.failWrites = true

        val failure = assertThrows(IOException::class.java) {
            HlsToMp4Postprocessing.copyOutput(fixture.mission, fixture.output)
        }

        assertEquals("Fixture storage is full", failure.message)
        assertTrue(fixture.storage.lastStream!!.isClosed)
        assertTrue(fixture.destination.length() in 1L until OUTPUT_BYTES.size.toLong())
        assertFalse(fixture.mission.psAlgorithm.worksOnSameFile)
        assertSame(plan, fixture.mission.hlsPlan)
        assertArrayEquals(CACHED_INPUT, fixture.readCachedInput())
        assertArrayEquals(OUTPUT_BYTES, fixture.output.readBytes())

        fixture.mission.resetState(true, false, DownloadMission.ERROR_NOTHING)
        assertEquals(1, fixture.mission.current)
        assertSame(plan, fixture.mission.hlsPlan)
        fixture.storage.failWrites = false
        HlsToMp4Postprocessing.copyOutput(fixture.mission, fixture.output)

        assertArrayEquals(OUTPUT_BYTES, fixture.destination.readBytes())
        assertEquals(OUTPUT_BYTES.size.toLong(), fixture.mission.length)
        assertEquals(fixture.mission.length, fixture.mission.done)
        assertArrayEquals(CACHED_INPUT, fixture.readCachedInput())
        assertEquals(1, fixture.mission.downloadCalls)
    }

    @Test
    fun cancelWithoutDeletingKeepsTheExistingDestination() = Fixture().use { fixture ->
        val existing = fixture.destination.readBytes()
        fixture.mission.cancel(false)

        assertThrows(InterruptedIOException::class.java) {
            HlsToMp4Postprocessing.copyOutput(fixture.mission, fixture.output)
        }

        assertArrayEquals(existing, fixture.destination.readBytes())
    }

    private inner class Fixture : Closeable {
        private val context = RuntimeEnvironment.getApplication()
        val destination = temporaryFolder.newFile("destination.mp4").apply { writeText("previous output") }
        val output = temporaryFolder.newFile("completed.mp4").apply { writeBytes(OUTPUT_BYTES) }
        val storage = ControlledStorage(context, destination)
        val mission = PreparedMission(context, storage)

        init {
            mission.start()
            await(mission.prepared)
            mission.threads.single().join(5_000)
            assertFalse(mission.threads.single().isAlive)
        }

        fun readCachedInput(): ByteArray {
            val cache = HlsDownloadCache.open(context, requireNotNull(mission.hlsPlan).cacheId)
            try {
                return DataSourceInputStream(HlsDownloadCache.offlineFactory(cache).createDataSource(), DataSpec(Uri.parse(SEGMENT_URL))).use { it.readBytes() }
            } finally {
                cache.release()
            }
        }

        override fun close() {
            mission.hlsPlan?.let { HlsDownloadCache.remove(context, it.cacheId) }
        }
    }

    private class ControlledStorage(context: Context, private val destination: File) : StoredFileHelper(context, null, Uri.fromFile(destination), "") {
        var beforeOpen: () -> Unit = {}
        var afterOpen: () -> Unit = {}
        var failWrites = false
        var lastStream: SharpStream? = null

        override fun getStream(): SharpStream {
            beforeOpen()
            val stream = if (failWrites) {
                object : FileStream(destination) {
                    override fun write(buffer: ByteArray, offset: Int, count: Int) {
                        super.write(buffer, offset, minOf(16, count))
                        throw IOException("Fixture storage is full")
                    }
                }
            } else {
                super.getStream()
            }
            lastStream = stream
            afterOpen()
            return stream
        }
    }

    private class PreparedMission(private val application: Context, storage: StoredFileHelper) : HlsDownloadMission(
        arrayOf(MANIFEST_URL),
        storage,
        'v',
        HlsToMp4Postprocessing()
    ) {
        val prepared = CountDownLatch(1)
        var downloadCalls = 0
            private set

        override fun context(): Context = application

        override fun download(cacheId: String, isCurrent: () -> Boolean, progress: (Long, Long) -> Unit): HlsDownloadPlan {
            downloadCalls++
            val cache = HlsDownloadCache.open(application, cacheId)
            try {
                val factory = HlsDownloadCache.networkFactory(cache, DataSource.Factory { ByteArrayDataSource(CACHED_INPUT) })
                DataSourceInputStream(factory.createDataSource(), DataSpec(Uri.parse(SEGMENT_URL))).use { it.readBytes() }
            } finally {
                cache.release()
            }
            return HlsDownloadPlan(cacheId, MANIFEST_URL, HashMap(), ArrayList(), MANIFEST_URL, null, 720, 3_000_000, "avc1.42e01e,mp4a.40.2", true)
        }

        override fun notifyFinished() {
            // Stop at the real network-to-postprocessing handoff; the tests exercise final copying.
            running = false
            prepared.countDown()
        }
    }

    companion object {
        private const val MANIFEST_URL = "https://fixture.example/index.m3u8"
        private const val SEGMENT_URL = "https://fixture.example/segment.ts"
        private val CACHED_INPUT = "retained private input".toByteArray()
        private val OUTPUT_BYTES = ByteArray(256 * 1024) { (it % 251).toByte() }

        private fun await(latch: CountDownLatch) = assertTrue("Fixture worker timed out", latch.await(5, TimeUnit.SECONDS))

        private fun awaitIgnoringInterrupt(latch: CountDownLatch) {
            var interrupted = false
            try {
                while (true) {
                    try {
                        if (!latch.await(5, TimeUnit.SECONDS)) throw AssertionError("Fixture destination open was not released")
                        return
                    } catch (_: InterruptedException) {
                        interrupted = true
                    }
                }
            } finally {
                if (interrupted) Thread.currentThread().interrupt()
            }
        }
    }
}
