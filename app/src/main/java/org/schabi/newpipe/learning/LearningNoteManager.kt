/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.learning

import android.content.Context
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.UUID
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.learning.model.LearningNoteEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.sync.HistorySyncRecorder

class LearningNoteManager(context: Context) {
    private val database = NewPipeDatabase.getInstance(context.applicationContext)
    private val noteDao = database.learningNoteDAO()
    private val streamDao = database.streamDAO()
    private val syncRecorder = HistorySyncRecorder.get(context.applicationContext)

    fun create(info: StreamInfo, timestampMillis: Long, noteText: String): Single<LearningNoteEntity> {
        return Single.fromCallable {
            database.runInTransaction<LearningNoteEntity> {
                val now = System.currentTimeMillis()
                val note = LearningNoteEntity(
                    noteId = UUID.randomUUID().toString(),
                    streamId = streamDao.upsert(StreamEntity(info)),
                    timestampMillis = timestampMillis.coerceAtLeast(0),
                    noteText = validateText(noteText),
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now
                )
                noteDao.upsert(note)
                syncRecorder.recordLearningNoteUpsert(note.noteId)
                note
            }
        }.subscribeOn(Schedulers.io())
    }

    fun update(
        note: LearningNoteEntity,
        timestampMillis: Long,
        noteText: String
    ): Single<LearningNoteEntity> {
        return Single.fromCallable {
            note.copy(
                timestampMillis = timestampMillis.coerceAtLeast(0),
                noteText = validateText(noteText),
                updatedAtEpochMillis = System.currentTimeMillis()
            ).also { updated ->
                database.runInTransaction {
                    noteDao.upsert(updated)
                    syncRecorder.recordLearningNoteUpsert(updated.noteId)
                }
            }
        }.subscribeOn(Schedulers.io())
    }

    fun delete(noteId: String): Completable = Completable.fromAction {
        database.runInTransaction {
            val note = noteDao.getNote(noteId) ?: return@runInTransaction
            syncRecorder.recordLearningNoteDelete(note)
            noteDao.delete(noteId)
        }
    }.subscribeOn(Schedulers.io())

    fun observe(serviceId: Int, url: String): Flowable<List<LearningNoteEntity>> {
        return streamDao.getStream(serviceId.toLong(), url)
            .switchMap { streams ->
                if (streams.isEmpty()) {
                    Flowable.just(emptyList())
                } else {
                    noteDao.getNotesForStream(streams.first().uid)
                }
            }
            .subscribeOn(Schedulers.io())
    }

    private fun validateText(value: String): String {
        val text = value.trim()
        require(text.isNotEmpty()) { "A learning note cannot be empty" }
        require(text.length <= MAX_NOTE_LENGTH) { "A learning note is too long" }
        return text
    }

    companion object {
        const val MAX_NOTE_LENGTH = 10_000
    }
}
