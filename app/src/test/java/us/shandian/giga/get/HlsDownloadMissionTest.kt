/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package us.shandian.giga.get

import android.app.Application
import android.content.Context
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.download.hls.HlsDownloadPlan
import org.schabi.newpipe.streams.io.StoredFileHelper
import us.shandian.giga.postprocessing.Postprocessing

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], application = Application::class)
class HlsDownloadMissionTest {
    @Test
    fun successfulNetworkPhaseAdvancesOnlyAfterItsWorkerReturns() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val mission = TestMission { id, _, progress ->
            started.countDown()
            release.await()
            progress(10, 10)
            plan(id)
        }
        mission.start()
        await(started)
        assertEquals(0, mission.current)
        assertEquals(0, mission.finishCalls.get())
        release.countDown()
        await(mission.finished)
        assertEquals(1, mission.current)
        assertEquals(10L, mission.done)
        assertNotNull(mission.hlsPlan)
        assertEquals(1, mission.finishCalls.get())
    }

    @Test
    fun managerForcePauseStillInterruptsWorkerAfterBaseThreadArrayWasCleared() {
        val started = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        val mission = TestMission { _, _, _ ->
            started.countDown()
            try {
                CountDownLatch(1).await()
            } catch (error: InterruptedException) {
                interrupted.countDown()
                throw error
            }
            error("unreachable")
        }
        mission.start()
        await(started)
        mission.threads = emptyArray()
        mission.pause()
        await(interrupted)
        assertFalse(mission.running)
        assertEquals(0, mission.current)
        assertEquals(0, mission.finishCalls.get())
    }

    @Test
    fun rapidResumeWaitsForOldWorkerAndIgnoresItsLateSuccess() {
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        val starts = AtomicInteger()
        val mission = TestMission { id, _, progress ->
            if (starts.incrementAndGet() == 1) {
                firstStarted.countDown()
                awaitIgnoringInterrupt(releaseFirst)
                plan(id).copy(height = 360)
            } else {
                secondStarted.countDown()
                progress(20, 20)
                plan(id).copy(height = 720)
            }
        }
        mission.start()
        await(firstStarted)
        mission.pause()
        mission.start()
        assertEquals(1, starts.get())
        releaseFirst.countDown()
        await(secondStarted)
        await(mission.finished)
        assertEquals(720, mission.hlsPlan!!.height)
        assertEquals(20L, mission.done)
        assertEquals(1, mission.finishCalls.get())
    }

    @Test
    fun repeatedPauseResumeKeepsTheOriginalCacheOwnerInThePredecessorChain() {
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val starts = AtomicInteger()
        val mission = TestMission { id, _, _ ->
            if (starts.incrementAndGet() == 1) {
                firstStarted.countDown()
                awaitIgnoringInterrupt(releaseFirst)
                plan(id).copy(height = 360)
            } else {
                plan(id).copy(height = 1080)
            }
        }
        mission.start()
        await(firstStarted)
        mission.pause()
        mission.start()
        val second = mission.threads.single()
        mission.pause()
        mission.start()
        second.join(200)
        assertEquals(1, starts.get())
        assertTrue("The cancelled waiter must retain its predecessor until release", second.isAlive)
        releaseFirst.countDown()
        await(mission.finished)
        assertEquals(2, starts.get())
        assertEquals(1080, mission.hlsPlan!!.height)
        assertEquals(1, mission.finishCalls.get())
    }

    @Test
    fun cancellationFencesLateErrorAndDoesNotPublishFinishedDownload() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val ended = CountDownLatch(1)
        val mission = TestMission { _, _, _ ->
            started.countDown()
            awaitIgnoringInterrupt(release)
            ended.countDown()
            throw IOException("late fixture failure")
        }
        mission.start()
        await(started)
        val worker = mission.threads.single()
        mission.cancel(true)
        release.countDown()
        await(ended)
        worker.join(5000)
        assertFalse(worker.isAlive)
        assertTrue(mission.deleted)
        assertFalse(mission.running)
        assertEquals(DownloadMission.ERROR_NOTHING, mission.errCode)
        assertEquals(0, mission.finishCalls.get())
    }

    @Test
    fun networkFailureBecomesAnErrorAndNeverStartsPostprocessing() {
        val mission = TestMission { _, _, _ -> throw IOException("fixture failure") }
        mission.start()
        val worker = mission.threads.single()
        worker.join(5000)
        assertFalse(worker.isAlive)
        assertEquals(DownloadMission.ERROR_UNKNOWN_EXCEPTION, mission.errCode)
        assertFalse(mission.running)
        assertEquals(0, mission.current)
        assertEquals(0, mission.finishCalls.get())
    }

    @Test
    fun recoveryResetRetainsCompleteOfflineInputForExportRetry() {
        val mission = TestMission { id, _, _ -> plan(id) }
        mission.start()
        await(mission.finished)
        val plan = mission.hlsPlan
        mission.resetState(true, false, DownloadMission.ERROR_NOTHING)
        assertEquals(1, mission.current)
        assertEquals(0, mission.psState)
        assertEquals(plan, mission.hlsPlan)
    }

    private class TestMission(private val work: (String, () -> Boolean, (Long, Long) -> Unit) -> HlsDownloadPlan) : HlsDownloadMission(
        arrayOf("https://fixture.example/master.m3u8"),
        Mockito.mock(StoredFileHelper::class.java).also {
            Mockito.`when`(it.existsAsFile()).thenReturn(true)
            Mockito.`when`(it.canWrite()).thenReturn(true)
            Mockito.`when`(it.delete()).thenReturn(true)
        },
        'v',
        Mockito.mock(Postprocessing::class.java)
    ) {
        val finishCalls = AtomicInteger()
        val finished = CountDownLatch(1)

        override fun context(): Context = RuntimeEnvironment.getApplication()

        override fun download(cacheId: String, isCurrent: () -> Boolean, progress: (Long, Long) -> Unit): HlsDownloadPlan = work(cacheId, isCurrent, progress)

        override fun notifyFinished() {
            finishCalls.incrementAndGet()
            running = false
            finished.countDown()
        }
    }

    companion object {
        private fun plan(id: String) = HlsDownloadPlan(
            id, "https://fixture.example/master.m3u8", HashMap(), ArrayList(),
            "https://fixture.example/video.m3u8", null, 360, 3_000_000, "avc1.42e01e"
        )

        private fun await(latch: CountDownLatch) = assertTrue("Fixture worker timed out", latch.await(5, TimeUnit.SECONDS))

        private fun awaitIgnoringInterrupt(latch: CountDownLatch) {
            while (true) {
                try {
                    latch.await()
                    return
                } catch (_: InterruptedException) {
                    // Deliberately emulate a late callback from an operation which ignores cancellation.
                }
            }
        }
    }
}
