/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.recovery

import android.app.Application
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.playqueue.LocalMediaPlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class QueueRecoveryStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val now = 1_000_000_000L

    private fun snapshot(session: String, time: Long = now): QueueSnapshot {
        val remote = PlayQueueItem(
            StreamInfoItem(0, "https://www.youtube.com/watch?v=abcdefghijk", "Remote", StreamType.VIDEO_STREAM).also {
                it.duration = 120
                it.uploaderName = "Channel"
            }
        )
        val local = PlayQueueItem.localMedia("Local", "content://media/external/audio/media/7", 200, "Artist", "Album", "Music", "audio/mpeg", 7, false, null)
        return requireNotNull(QueueSnapshot.capture(LocalMediaPlayQueue(listOf(remote, local), 1), session, 35_000, time))
    }

    @Test fun roundTripPreservesMixedQueueOrderSelectionAndPosition() {
        val store = QueueRecoveryStore(File(temporary.root, "profile.json"))
        val snapshot = snapshot("one")
        store.save(snapshot)
        val loaded = store.load(now).single()
        assertEquals(snapshot, loaded)
        val queue = loaded.queue()
        assertEquals(listOf("Remote", "Local"), queue.streams.map { it.title })
        assertEquals(1, queue.index)
        assertEquals(35_000L, queue.item!!.recoveryPosition)
        assertTrue(queue.item!!.isLocalMedia)
        assertEquals("audio/mpeg", queue.item!!.mimeType)
        assertEquals(7L, queue.item!!.localMediaId)
    }

    @Test fun checkpointsDoNotEvictPreviousQueueButThirdSessionDoes() {
        val store = QueueRecoveryStore(File(temporary.root, "profile.json"))
        store.save(snapshot("one"))
        store.save(snapshot("two", now + 1))
        store.save(snapshot("two", now + 2).copy(position = 80_000))
        assertEquals(listOf("two", "one"), store.load(now + 2).map { it.session })
        assertEquals(80_000L, store.load(now + 2).first().position)
        store.save(snapshot("three", now + 3))
        assertEquals(listOf("three", "two"), store.load(now + 3).map { it.session })
    }

    @Test fun expiredFutureCorruptAndOversizedFilesAreIgnored() {
        val file = File(temporary.root, "profile.json")
        val store = QueueRecoveryStore(file)
        store.save(snapshot("one"))
        assertTrue(store.load(now + QueueRecoveryStore.MAX_AGE + 1).isEmpty())
        assertFalse(file.exists())
        store.save(snapshot("one"))
        assertTrue(store.load(now - 1).isEmpty())
        assertFalse(file.exists())
        file.writeText("{broken")
        assertTrue(store.load(now).isEmpty())
        file.writeBytes(ByteArray(QueueRecoveryStore.MAX_BYTES + 1))
        assertTrue(store.load(now).isEmpty())
    }

    @Test fun clearingOneProfileDoesNotRemoveAnother() {
        val first = QueueRecoveryStore(File(temporary.root, "one.json"))
        val second = QueueRecoveryStore(File(temporary.root, "two.json"))
        first.save(snapshot("one"))
        second.save(snapshot("two"))
        first.clear()
        assertTrue(first.load(now).isEmpty())
        assertEquals("two", second.load(now).single().session)
    }

    @Test fun liveStreamsDoNotRestoreAnObsoletePosition() {
        val snapshot = snapshot("live")
        val live = snapshot.copy(entries = listOf(snapshot.entries[0].copy(type = "LIVE_STREAM")), index = 0)
        assertEquals(PlayQueueItem.RECOVERY_UNSET, live.queue().item!!.recoveryPosition)
    }

    @Test fun invalidIndexAndOversizedQueuesAreRejected() {
        val snapshot = snapshot("one")
        assertThrows(IllegalArgumentException::class.java) { QueueSnapshot.parse(snapshot.json().put("index", 10)) }
        val item = snapshot.entries[0].item()
        assertNull(QueueSnapshot.capture(LocalMediaPlayQueue(List(1001) { item }, 0), "large", 0, now))
    }
}
