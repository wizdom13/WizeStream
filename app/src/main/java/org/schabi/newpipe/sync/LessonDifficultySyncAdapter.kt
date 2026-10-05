package org.schabi.newpipe.sync

import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import org.schabi.newpipe.database.sync.StructuredPreferenceSyncRecordEntity
import org.schabi.newpipe.learning.LearningDifficulty
import org.schabi.newpipe.learning.LearningDifficultyIdentity

internal class LessonDifficultySyncAdapter(
    private val preferences: SharedPreferences,
    private val records: StructuredPreferenceRecordRepository,
    private val canMaterializeProfile: (String) -> Boolean
) : StructuredPreferenceCategoryAdapter {
    override val category = StructuredPreferenceCategory.LESSON_DIFFICULTY

    override fun snapshotHash(): String = structuredPreferenceDigest(
        STRUCTURED_PREFERENCE_JSON.encodeToString(currentRatings())
    )

    override fun reconcile(bootstrap: Boolean) {
        val desired = currentRatings().associateBy { structuredPreferenceDigest(it.key) }
        desired.forEach { (id, rating) ->
            records.saveLocalUpsert(
                category,
                id,
                StructuredPreferenceRecordType.LESSON_DIFFICULTY,
                SyncedStructuredPreferenceRecord(lessonDifficulty = rating)
            )
        }
        records.getRecordsByType(category, StructuredPreferenceRecordType.LESSON_DIFFICULTY)
            .filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .filter { accessible(records.decodeRecord(it).lessonDifficulty!!.key) }
            .filterNot { it.recordId in desired }
            .forEach(records::saveLocalDelete)
    }

    override fun materialize() {
        val editor = preferences.edit()
        preferences.all.keys.filter { LearningDifficultyIdentity.isValid(it) && accessible(it) }
            .forEach(editor::remove)
        records.getRecordsByType(category, StructuredPreferenceRecordType.LESSON_DIFFICULTY)
            .filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .mapNotNull { records.decodeRecord(it).lessonDifficulty }
            .filter { accessible(it.key) }
            .forEach { editor.putInt(it.key, it.rating) }
        check(editor.commit()) { "Unable to save synchronized lesson difficulty" }
    }

    private fun accessible(key: String): Boolean = canMaterializeProfile(LearningDifficultyIdentity.profileId(key))

    private fun currentRatings(): List<SyncedLessonDifficulty> = preferences.all.entries
        .mapNotNull { (key, value) ->
            if (
                key.startsWith(LearningDifficulty.PREFIX) &&
                LearningDifficultyIdentity.isValid(key) && accessible(key) &&
                value is Int && value in 0..3
            ) {
                SyncedLessonDifficulty(key, value)
            } else {
                null
            }
        }.sortedBy(SyncedLessonDifficulty::key)
}
