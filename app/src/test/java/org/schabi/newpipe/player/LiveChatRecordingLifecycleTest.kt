package org.schabi.newpipe.player

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.preference.PreferenceManager
import java.io.File
import java.io.IOException
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItem
import org.schabi.newpipe.local.history.IncognitoMode
import org.schabi.newpipe.player.LiveChatRecorder.Archive
import org.schabi.newpipe.player.LiveChatRecorder.ArchiveStore
import org.schabi.newpipe.player.LiveChatRecorder.Controller
import org.schabi.newpipe.player.LiveChatRecorder.Entry
import org.schabi.newpipe.player.LiveChatRecorder.Failure
import org.schabi.newpipe.player.LiveChatRecorder.Key
import org.schabi.newpipe.player.LiveChatRecorder.Phase
import org.schabi.newpipe.profiles.ProfileManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class LiveChatRecordingLifecycleTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()
    private val preferences get() = PreferenceManager.getDefaultSharedPreferences(context)
    private lateinit var scope: CoroutineScope
    private lateinit var source: Channel<Result<List<BulletCommentsInfoItem>>>
    private lateinit var recorder: Controller
    private var wallTime = 1_000_000L
    private var sourceStarts = 0
    private val key get() = Key(ProfileManager.getActiveProfileId(context), SERVICE, URL)

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        File(context.filesDir, "recorded-live-chats").deleteRecursively()
        scope = CoroutineScope(SupervisorJob() + Handler(Looper.getMainLooper()).asCoroutineDispatcher())
        source = Channel(Channel.UNLIMITED)
        recorder = controller()
    }

    @After
    fun tearDown() {
        IncognitoMode.setEnabled(context, true)
        idle()
        scope.cancel()
        idle()
    }

    @Test
    fun immediateStopBeforeFirstDispatchReleasesTheOwnerAndAllowsAnotherExplicitStart() {
        assertTrue(recorder.start(context, SERVICE, URL))
        assertEquals(Phase.STARTING, phase())
        recorder.stop(SERVICE, URL)
        assertEquals(Phase.SAVING, phase())
        assertFalse(recorder.isRecording(SERVICE, URL))
        assertFalse(recorder.start(context, SERVICE, URL))
        idle()

        assertEquals(0, sourceStarts)
        assertEquals(Phase.PAUSED, phase())
        assertFalse(LiveChatRecorder.hasSaved(context, SERVICE, URL))
        assertTrue(recorder.start(context, SERVICE, URL))
        idle()
        assertEquals(1, sourceStarts)
        assertEquals(Phase.RECORDING, phase())
    }

    @Test
    fun stoppingKeepsTheOwnerUntilDelayedSaveFinishesAndExcludesSaveTimeFromTheBudget() {
        val saveGate = CompletableDeferred<Unit>()
        var saves = 0
        var elapsed = 0L
        recorder = controller(
            object : ArchiveStore() {
                override suspend fun save(context: Context, key: Key, archive: Archive, allowed: () -> Boolean): Boolean {
                    saves++
                    saveGate.await()
                    return super.save(context, key, archive, allowed)
                }
            },
            elapsedTime = { elapsed }
        )
        start()
        advance(3_000)
        elapsed = 3_000
        send(message("first"))
        recorder.stop(SERVICE, URL)
        assertEquals(Phase.SAVING, phase())
        // A second Stop before cancellation cleanup must keep the first accepted stop time.
        elapsed = 11_000
        recorder.stop(SERVICE, URL)
        idle()
        assertEquals(1, saves)
        assertFalse(recorder.start(context, SERVICE, URL))
        assertFalse(recorder.start(context, SERVICE, "$URL/other"))
        advance(8_000)
        recorder.stop(SERVICE, URL)
        saveGate.complete(Unit)
        idle()

        assertEquals(Phase.PAUSED, phase())
        assertEquals(3_000L, archive().capturedMillis)
        assertTrue(recorder.start(context, SERVICE, URL))
        idle()
        assertEquals(Phase.RECORDING, phase())
        assertEquals(2, sourceStarts)
        send(message("second"))
        stop()
        assertEquals(listOf("first", "second"), saved().map(Entry::text))
        assertEquals(2, saves)
    }

    @Test
    fun resumingAfterControllerRecreationAppendsPreservesTimelineAndDeduplicatesReplayOffsets() {
        start()
        advance(2_000)
        send(message("live first"), message("replay", 5_000))
        stop()
        val originalAnchor = archive().startedAt
        advance(60_000)
        recorder = controller()
        start()
        advance(3_000)
        send(message("replay", 5_000), message("live second"))
        stop()

        val rows = saved()
        assertEquals(listOf("live first", "replay", "live second"), rows.map(Entry::text))
        assertEquals(listOf(2_000L, 5_000L, 65_000L), rows.map(Entry::atMillis))
        assertEquals(originalAnchor, archive().startedAt)
        assertEquals(5_000L, archive().capturedMillis)
        assertEquals(rows.take(2), LiveChatRecorder.replayRows(rows, 7_000))
    }

    @Test
    fun unreadableExistingArchiveDoesNotStartCaptureOrReplaceItsBytes() {
        start()
        send(message("keep"))
        stop()
        val file = savedFile()
        val corrupt = byteArrayOf(1, 2, 3, 4)
        file.writeBytes(corrupt)
        recorder = controller()
        start()

        assertEquals(Phase.READ_FAILED, phase())
        assertEquals(Failure.READ, recorder.state.value?.failure)
        assertEquals(1, sourceStarts)
        assertTrue(corrupt.contentEquals(file.readBytes()))
        assertFalse(recorder.isRecording(SERVICE, URL))
    }

    @Test
    fun failedSaveKeepsThePreviousFileAndRetainsNewRowsForAnExplicitSaveRetry() {
        start()
        send(message("before", 1_000))
        stop()
        val oldBytes = savedFile().readBytes()
        var fail = true
        recorder = controller(object : ArchiveStore() {
            override suspend fun save(context: Context, key: Key, archive: Archive, allowed: () -> Boolean): Boolean {
                if (fail) throw IOException("Storage unavailable")
                return super.save(context, key, archive, allowed)
            }
        })
        start()
        send(message("after", 2_000))
        stop()

        assertEquals(Phase.SAVE_FAILED, phase())
        assertTrue(oldBytes.contentEquals(savedFile().readBytes()))
        assertEquals(listOf("before"), saved().map(Entry::text))
        assertFalse(recorder.start(context, SERVICE, URL))
        fail = false
        assertTrue(recorder.retrySaving(context, SERVICE, URL))
        assertEquals(Phase.SAVING, phase())
        idle()
        assertEquals(Phase.PAUSED, phase())
        assertEquals(listOf("before", "after"), saved().map(Entry::text))
        assertEquals(2, sourceStarts)
    }

    @Test
    fun transientResultFailuresDoNotStopCaptureButActualCompletionSaves() {
        start()
        assertTrue(source.trySend(Result.failure(IOException("Retrying"))).isSuccess)
        idle()
        assertEquals(Phase.RECORDING, phase())
        send(message("recovered"))
        source.close()
        idle()

        assertEquals(Phase.PAUSED, phase())
        assertNull(recorder.state.value?.failure)
        assertEquals(listOf("recovered"), saved().map(Entry::text))
    }

    @Test
    fun thrownSourceFailureSavesThePartialCaptureAndReleasesTheOwner() {
        recorder = controller(messages = { _, _ ->
            flow {
                emit(Result.success(listOf(message("partial"))))
                throw IOException("Disconnected")
            }
        })
        start()

        assertEquals(Phase.PAUSED, phase())
        assertEquals(Failure.SOURCE, recorder.state.value?.failure)
        assertEquals(listOf("partial"), saved().map(Entry::text))
        assertTrue(recorder.start(context, SERVICE, URL))
    }

    @Test
    fun timeoutUsesRemainingCumulativeBudgetAndCannotBeBypassedByResuming() {
        seed(Archive(wallTime - 90_000, LiveChatRecorder.MAX_DURATION_MS - 1_000, mutableListOf(Entry(500, "A", "earlier"))))
        start()
        send(message("last second"))
        advance(999)
        assertEquals(Phase.RECORDING, phase())
        advance(1)

        assertEquals(Phase.LIMIT, phase())
        assertEquals(LiveChatRecorder.MAX_DURATION_MS, archive().capturedMillis)
        assertEquals(2, saved().size)
        recorder = controller()
        start()
        assertEquals(Phase.LIMIT, phase())
        assertEquals(1, sourceStarts)
    }

    @Test
    fun switchingProfilesDuringDelayedSavePreservesOnlyTheEarlierSourceProfileArchive() {
        val originalProfile = key.profileId
        val other = requireNotNull(ProfileManager.createProfile(context, "Other recording profile"))
        assertTrue(ProfileManager.setActiveProfile(context, originalProfile))
        start()
        send(message("before switch", 1_000))
        stop()
        val before = savedFile().readBytes()
        val gate = CompletableDeferred<Unit>()
        recorder = controller(object : ArchiveStore() {
            override suspend fun save(context: Context, key: Key, archive: Archive, allowed: () -> Boolean): Boolean {
                gate.await()
                return super.save(context, key, archive, allowed)
            }
        })
        start()
        send(message("discard on switch", 2_000))
        recorder.stop(SERVICE, URL)
        idle()
        assertEquals(Phase.SAVING, phase())

        assertTrue(ProfileManager.setActiveProfile(context, other.id))
        idle()
        assertFalse(LiveChatRecorder.hasSaved(context, SERVICE, URL))
        assertTrue(saved().isEmpty())
        gate.complete(Unit)
        idle()
        assertNull(recorder.state.value)
        assertTrue(before.contentEquals(savedFile().readBytes()))

        assertTrue(ProfileManager.setActiveProfile(context, originalProfile))
        idle()
        assertEquals(listOf("before switch"), saved().map(Entry::text))
        assertFalse(recorder.isRecording(SERVICE, URL))
        assertEquals(2, sourceStarts)
    }

    @Test
    fun resumedMessageLimitCountsEarlierRowsAndKeepsOnlyTheRemainingSlot() {
        seed(Archive(wallTime, rows = (1 until LiveChatRecorder.MAX_MESSAGES).map { Entry(it.toLong(), "A", "$it") }.toMutableList()))
        start()
        send(message("1", 1), message("last", 8_000), message("over the limit", 9_000))

        assertEquals(Phase.LIMIT, phase())
        assertEquals(LiveChatRecorder.MAX_MESSAGES, saved().size)
        assertEquals("last", saved().last().text)
    }

    @Test
    fun incognitoEnteredDuringFinalCommitPreservesTheEarlierArchiveAndDiscardsNewRows() {
        start()
        send(message("before", 1_000))
        stop()
        val before = savedFile().readBytes()
        recorder = controller(object : ArchiveStore() {
            override suspend fun save(context: Context, key: Key, archive: Archive, allowed: () -> Boolean): Boolean {
                var checks = 0
                return super.save(context, key, archive) {
                    if (++checks == 2) IncognitoMode.setEnabled(context, true)
                    allowed()
                }
            }
        })
        start()
        send(message("private", 2_000))
        stop()

        assertNull(recorder.state.value)
        assertTrue(before.contentEquals(savedFile().readBytes()))
        assertTrue(saved().isEmpty())
        IncognitoMode.setEnabled(context, false)
        idle()
        assertEquals(listOf("before"), saved().map(Entry::text))
        assertEquals(2, sourceStarts)
    }

    @Test
    fun deletingTheProfileAtFinalCommitCannotResurrectItsArchiveOrStartDuringMetadataRemoval() {
        val profileId = key.profileId
        recorder = controller(object : ArchiveStore() {
            override suspend fun save(context: Context, key: Key, archive: Archive, allowed: () -> Boolean): Boolean {
                var checks = 0
                return super.save(context, key, archive) {
                    if (++checks == 2) recorder.forgetProfile(context, key.profileId)
                    allowed()
                }
            }
        })
        start()
        send(message("discard"))
        stop()

        assertEquals(profileId, ProfileManager.getActiveProfileId(context))
        assertNull(recorder.state.value)
        assertFalse(LiveChatRecorder.hasSaved(context, SERVICE, URL))
        assertTrue(File(context.filesDir, "recorded-live-chats").walk().none { it.isFile })
        assertFalse(recorder.start(context, SERVICE, URL))
    }

    @Test
    fun privacyChangeStopsCaptureEvenWhenNoFurtherMessagesArrive() {
        start()
        send(message("discard"))
        IncognitoMode.setEnabled(context, true)
        idle()

        assertNull(recorder.state.value)
        assertFalse(recorder.isRecording(SERVICE, URL))
        IncognitoMode.setEnabled(context, false)
        idle()
        assertFalse(LiveChatRecorder.hasSaved(context, SERVICE, URL))
        assertEquals(1, sourceStarts)
    }

    @Test
    fun legacyArchiveBudgetUsesWriteTimeInsteadOfReplayVideoOffsets() {
        seed(Archive(wallTime - 4_000, rows = mutableListOf(Entry(7_200_000, "A", "two hours into a replay"))))
        val file = savedFile()
        val json = java.util.zip.GZIPInputStream(file.inputStream()).bufferedReader().use { org.json.JSONObject(it.readText()) }
        json.remove("capturedMillis")
        java.util.zip.GZIPOutputStream(file.outputStream()).bufferedWriter().use { it.write(json.toString()) }
        assertTrue(file.setLastModified(wallTime))
        start()
        assertEquals(Phase.RECORDING, phase())
        stop()

        assertEquals(4_000L, archive().capturedMillis)
        assertEquals(7_200_000L, saved().single().atMillis)
    }

    private fun controller(
        store: ArchiveStore = ArchiveStore(),
        messages: (Int, String) -> Flow<Result<List<BulletCommentsInfoItem>>> = { _, _ ->
            sourceStarts++
            source.receiveAsFlow()
        },
        elapsedTime: () -> Long = SystemClock::elapsedRealtime
    ) = Controller(scope, messages, { wallTime }, elapsedTime, store)

    private fun start() {
        assertTrue(recorder.start(context, SERVICE, URL))
        idle()
    }

    private fun stop() {
        recorder.stop(SERVICE, URL)
        idle()
    }

    private fun send(vararg messages: BulletCommentsInfoItem) {
        assertTrue(source.trySend(Result.success(messages.toList())).isSuccess)
        idle()
    }

    private fun message(text: String, offset: Long = 0) = BulletCommentsInfoItem(SERVICE, URL, "A").apply {
        commentText = text
        duration = Duration.ofMillis(offset)
    }

    private fun advance(millis: Long) {
        wallTime += millis
        shadowOf(Looper.getMainLooper()).idleFor(millis, TimeUnit.MILLISECONDS)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun phase() = recorder.state.value?.phase
    private fun saved() = LiveChatRecorder.read(context, SERVICE, URL)
    private fun archive() = runBlocking { requireNotNull(ArchiveStore().load(context, key)) }
    private fun seed(archive: Archive) = runBlocking { assertTrue(ArchiveStore().save(context, key, archive) { true }) }
    private fun savedFile(): File = File(context.filesDir, "recorded-live-chats").walk().single { it.name.endsWith(".json.gz") }

    companion object {
        private const val SERVICE = 0
        private const val URL = "https://example.com/live/recording"
    }
}
