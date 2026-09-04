/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.schabi.newpipe.R
import org.schabi.newpipe.database.sync.StructuredPreferenceSyncRecordEntity

internal class FilterSyncAdapter(
    private val context: Context,
    private val preferences: SharedPreferences,
    private val recordRepository: StructuredPreferenceRecordRepository
) : StructuredPreferenceCategoryAdapter {
    override val category = StructuredPreferenceCategory.FILTERS

    override fun snapshotHash(): String {
        val snapshot = filterSpecs().map { spec ->
            FilterSnapshot(spec.id, currentFilterValues(spec).sorted())
        }
        return structuredPreferenceDigest(
            STRUCTURED_PREFERENCE_JSON.encodeToString(snapshot)
        )
    }

    override fun reconcile(bootstrap: Boolean) {
        filterSpecs().forEach { spec ->
            val filter = SyncedFilterSet(
                filterId = spec.id,
                values = currentFilterValues(spec).sorted()
            )
            recordRepository.saveLocalUpsert(
                category = category,
                recordId = StructuredPreferenceRecordId.filterSet(spec.id),
                recordType = StructuredPreferenceRecordType.FILTER_SET,
                record = SyncedStructuredPreferenceRecord(filterSet = filter)
            )
        }
    }

    override fun materialize() {
        val specs = filterSpecs().associateBy(FilterSpec::id)
        val editor = preferences.edit()
        recordRepository.getRecordsByType(
            category,
            StructuredPreferenceRecordType.FILTER_SET
        ).filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .mapNotNull { recordRepository.decodeRecord(it).filterSet }
            .forEach { filter ->
                val spec = specs[filter.filterId] ?: return@forEach
                editor.putStringSet(spec.preferenceKey, filter.values.toSet())
            }
        editor.commit()
    }

    private fun filterSpecs(): List<FilterSpec> = listOf(
        FilterSpec(
            StructuredFilterId.CHANNEL_TABS,
            context.getString(R.string.show_channel_tabs_key),
            R.array.show_channel_tabs_value_list
        ),
        FilterSpec(
            StructuredFilterId.FEED_CHANNEL_TABS,
            context.getString(R.string.feed_fetch_channel_tabs_key),
            R.array.feed_fetch_channel_tabs_value_list
        ),
        FilterSpec(
            StructuredFilterId.SEARCH_SUGGESTIONS,
            context.getString(R.string.show_search_suggestions_key),
            R.array.show_search_suggestions_value_list
        )
    )

    private fun currentFilterValues(spec: FilterSpec): Set<String> {
        val defaults = context.resources.getStringArray(spec.defaultValuesResource).toSet()
        return preferences.getStringSet(spec.preferenceKey, defaults)?.toSet() ?: defaults
    }

    @Serializable
    private data class FilterSnapshot(
        val filterId: StructuredFilterId,
        val values: List<String>
    )

    private data class FilterSpec(
        val id: StructuredFilterId,
        val preferenceKey: String,
        val defaultValuesResource: Int
    )
}
