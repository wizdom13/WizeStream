/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.learning

import android.content.Context
import android.os.SystemClock
import android.util.Log
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.learning.model.LearningSessionEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.player.playqueue.PlayQueueItem

/** Records real wall-clock playback intervals while Learning Mode is active. */
class LearningSessionTracker(context: Context) {
    private val appContext = context.applicationContext
    private val database = NewPipeDatabase.getInstance(appContext)
    private val learningContent = LearningContentManager.getInstance(appContext)
    private var active: ActiveSession? = null

    @Synchronized
    fun update(item: PlayQueueItem?, playing: Boolean, backgroundPlayback: Boolean) {
        val nowEpochMillis = System.currentTimeMillis()
        val nowElapsedMillis = SystemClock.elapsedRealtime()
        accrue(nowEpochMillis, nowElapsedMillis)

        val eligible = item != null && playing && LearningMode.isEnabled(appContext) &&
            learningContent.isStreamLearning(item.serviceId, item.url) &&
            (!backgroundPlayback || LearningMode.shouldCountBackgroundPlayback(appContext))
        val current = active
        val localDate = localDate(nowEpochMillis)
        if (!eligible) {
            finish(nowEpochMillis)
            return
        }

        if (current == null || !current.matches(item, backgroundPlayback, localDate)) {
            finish(nowEpochMillis)
            active = ActiveSession(
                sessionId = UUID.randomUUID().toString(),
                stream = StreamEntity(item),
                startedAtEpochMillis = nowEpochMillis,
                endedAtEpochMillis = nowEpochMillis,
                watchedDurationMillis = 0,
                localDate = localDate,
                backgroundPlayback = backgroundPlayback,
                lastSampleElapsedMillis = nowElapsedMillis,
                lastPersistedDurationMillis = 0
            )
            return
        }

        if (current.watchedDurationMillis - current.lastPersistedDurationMillis >= FLUSH_INTERVAL_MS) {
            persist(current)
            current.lastPersistedDurationMillis = current.watchedDurationMillis
        }
    }

    @Synchronized
    fun stop() {
        val nowEpochMillis = System.currentTimeMillis()
        accrue(nowEpochMillis, SystemClock.elapsedRealtime())
        finish(nowEpochMillis)
    }

    private fun accrue(nowEpochMillis: Long, nowElapsedMillis: Long) {
        active?.let { current ->
            val elapsed = (nowElapsedMillis - current.lastSampleElapsedMillis).coerceAtLeast(0)
            current.watchedDurationMillis += elapsed
            current.endedAtEpochMillis = nowEpochMillis
            current.lastSampleElapsedMillis = nowElapsedMillis
        }
    }

    private fun finish(nowEpochMillis: Long) {
        active?.let { current ->
            current.endedAtEpochMillis = nowEpochMillis
            if (current.watchedDurationMillis >= MIN_SESSION_DURATION_MS) {
                persist(current)
            }
        }
        active = null
    }

    private fun persist(session: ActiveSession) {
        val snapshot = session.toEntity()
        val stream = session.stream.copy()
        Completable.fromAction {
            val streamId = database.streamDAO().upsert(stream)
            database.learningSessionDAO().upsert(snapshot.copy(streamId = streamId))
        }.subscribeOn(Schedulers.single()).subscribe(
            {},
            { error -> Log.e(TAG, "Could not persist learning session", error) }
        )
    }

    private fun localDate(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .toString()

    private data class ActiveSession(
        val sessionId: String,
        val stream: StreamEntity,
        val startedAtEpochMillis: Long,
        var endedAtEpochMillis: Long,
        var watchedDurationMillis: Long,
        val localDate: String,
        val backgroundPlayback: Boolean,
        var lastSampleElapsedMillis: Long,
        var lastPersistedDurationMillis: Long
    ) {
        fun matches(item: PlayQueueItem, background: Boolean, date: String): Boolean = stream.serviceId == item.serviceId && stream.url == item.url &&
            backgroundPlayback == background && localDate == date

        fun toEntity() = LearningSessionEntity(
            sessionId = sessionId,
            streamId = 0,
            startedAtEpochMillis = startedAtEpochMillis,
            endedAtEpochMillis = endedAtEpochMillis,
            watchedDurationMillis = watchedDurationMillis,
            localDate = localDate,
            backgroundPlayback = backgroundPlayback,
            designatedLearningContent = true
        )
    }

    companion object {
        private const val TAG = "LearningSessionTracker"
        private const val FLUSH_INTERVAL_MS = 15_000L
        private const val MIN_SESSION_DURATION_MS = 1_000L
    }
}
