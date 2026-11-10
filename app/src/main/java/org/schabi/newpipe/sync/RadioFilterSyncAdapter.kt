package org.schabi.newpipe.sync

import android.content.Context
import kotlinx.serialization.encodeToString
import org.schabi.newpipe.local.radio.RadioFilterPreferences
import org.schabi.newpipe.local.radio.RadioFilters
import org.schabi.newpipe.profiles.ProfileManager

internal class RadioFilterSyncAdapter(
    private val context: Context,
    private val records: StructuredPreferenceRecordRepository,
    private val canMaterializeProfile: (String) -> Boolean
) : StructuredPreferenceCategoryAdapter {
    override val category = StructuredPreferenceCategory.RADIO_FILTERS
    private val preferences = RadioFilterPreferences(context)
    private val recordType = StructuredPreferenceRecordType.RADIO_FILTERS

    override fun snapshotHash(): String = structuredPreferenceDigest(
        STRUCTURED_PREFERENCE_JSON.encodeToString(currentFilters())
    )

    override fun reconcile(bootstrap: Boolean) {
        val desired = currentFilters().associateBy { StructuredPreferenceRecordId.radioFilters(it.profileId) }
        desired.forEach { (id, filters) ->
            records.saveLocalUpsert(category, id, recordType, SyncedStructuredPreferenceRecord(radioFilters = filters))
        }
        records.getRecordsByType(category, recordType)
            .filterNot { it.isDeleted || it.recordId in desired }
            .filter { canMaterializeProfile(requireNotNull(records.decodeRecord(it).radioFilters).profileId) }
            .forEach(records::saveLocalDelete)
    }

    override fun materialize() {
        val changes = records.getRecordsByType(category, recordType).mapNotNull { record ->
            val synced = requireNotNull(records.decodeRecord(record).radioFilters)
            if (!canMaterializeProfile(synced.profileId)) return@mapNotNull null
            val filters = if (record.isDeleted) {
                RadioFilters()
            } else {
                RadioFilters(
                    country = synced.country,
                    language = synced.language,
                    genre = synced.genre
                )
            }
            synced.profileId to filters
        }.toMap()
        preferences.saveSynced(changes)
    }

    private fun currentFilters(): List<SyncedRadioFilters> = ProfileManager.getProfiles(context)
        .asSequence()
        .map { it.id }
        .filter(canMaterializeProfile)
        .map { profileId ->
            val filters = preferences.read(profileId)
            SyncedRadioFilters(profileId, filters.country, filters.language, filters.genre)
        }
        .filter { it.hasSelection() }
        .sortedBy { it.profileId }
        .toList()
}
