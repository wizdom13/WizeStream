/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.AiSListSyncWorker
import us.shandian.giga.get.sqlite.FinishedMissionStore

internal interface StructuredPreferenceSyncStore {
    val localPeerId: String

    fun reconcileLocal(category: StructuredPreferenceCategory)

    fun getKnownRevisions(category: StructuredPreferenceCategory): Map<String, Long>

    fun getPendingChanges(
        category: StructuredPreferenceCategory,
        peerId: String,
        limit: Int
    ): StructuredPreferenceChangeBatch

    fun acknowledgePeer(
        category: StructuredPreferenceCategory,
        peerId: String,
        knownRevisions: Map<String, Long>
    )

    fun applyChanges(
        category: StructuredPreferenceCategory,
        changes: List<StructuredPreferenceChange>
    ): StructuredPreferenceApplyResult

    fun clearPeerKnowledge()
}

internal class RoomStructuredPreferenceSyncStore internal constructor(
    private val context: Context,
    private val database: AppDatabase,
    override val localPeerId: String,
    private val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context),
    finishedMissionStore: FinishedMissionStore = FinishedMissionStore(context),
    canMaterializeProfile: (String) -> Boolean = { profileId ->
        ProfileManager.getProfile(context, profileId) != null
    },
    private val onAiSListEnabledChanged: (Boolean) -> Unit = {}
) : StructuredPreferenceSyncStore {
    private val recordRepository = StructuredPreferenceRecordRepository(database, localPeerId)
    private val completedDownloadAdapter = CompletedDownloadSyncAdapter(
        localPeerId,
        finishedMissionStore,
        recordRepository
    )
    private val adapters = listOf(
        FeedGroupSyncAdapter(
            database,
            recordRepository,
            canMaterializeProfile
        ),
        HomeTabSyncAdapter(context, preferences, database, recordRepository),
        ChannelProfileSyncAdapter(preferences, recordRepository),
        FilterSyncAdapter(context, preferences, recordRepository),
        ContentBlockingSyncAdapter(context, preferences, recordRepository),
        PortableSettingsSyncAdapter(context, preferences, recordRepository),
        completedDownloadAdapter
    ).associateBy(StructuredPreferenceCategoryAdapter::category)

    override fun reconcileLocal(category: StructuredPreferenceCategory) {
        database.runInTransaction {
            val adapter = adapter(category)
            val currentSnapshot = adapter.snapshotHash()
            val hasSnapshot = recordRepository.hasSnapshot(category)
            if (recordRepository.isSnapshotCurrent(category, currentSnapshot)) {
                return@runInTransaction
            }
            adapter.reconcile(bootstrap = !hasSnapshot)
            recordRepository.saveSnapshot(category, adapter.snapshotHash())
        }
    }

    override fun getKnownRevisions(
        category: StructuredPreferenceCategory
    ): Map<String, Long> {
        return recordRepository.getKnownRevisions(category)
    }

    override fun getPendingChanges(
        category: StructuredPreferenceCategory,
        peerId: String,
        limit: Int
    ): StructuredPreferenceChangeBatch {
        return recordRepository.getPendingChanges(category, peerId, limit)
    }

    override fun acknowledgePeer(
        category: StructuredPreferenceCategory,
        peerId: String,
        knownRevisions: Map<String, Long>
    ) {
        recordRepository.acknowledgePeer(category, peerId, knownRevisions)
    }

    override fun applyChanges(
        category: StructuredPreferenceCategory,
        changes: List<StructuredPreferenceChange>
    ): StructuredPreferenceApplyResult {
        val adapter = adapter(category)
        val aiSListKey = context.getString(org.schabi.newpipe.R.string.aislist_enabled_key)
        val previousAiSListEnabled = if (category == StructuredPreferenceCategory.CONTENT_BLOCKING) {
            preferences.getBoolean(aiSListKey, false)
        } else {
            false
        }
        val result = recordRepository.applyChanges(category, changes) {
            adapter.materialize()
            recordRepository.saveSnapshot(category, adapter.snapshotHash())
        }
        if (
            category == StructuredPreferenceCategory.CONTENT_BLOCKING &&
            result.affectedRecords > 0
        ) {
            val aiSListEnabled = preferences.getBoolean(aiSListKey, false)
            if (aiSListEnabled != previousAiSListEnabled) {
                onAiSListEnabledChanged(aiSListEnabled)
            }
        }
        return result
    }

    override fun clearPeerKnowledge() {
        recordRepository.clearPeerKnowledge()
    }

    internal fun getCompletedDownloadMetadata(): List<SyncedCompletedDownload> {
        return completedDownloadAdapter.getMetadata()
    }

    private fun adapter(
        category: StructuredPreferenceCategory
    ): StructuredPreferenceCategoryAdapter {
        return requireNotNull(adapters[category]) {
            "No structured preference adapter for $category"
        }
    }

    companion object {
        @Volatile
        private var instance: RoomStructuredPreferenceSyncStore? = null

        fun get(context: Context): RoomStructuredPreferenceSyncStore {
            return instance ?: synchronized(this) {
                instance ?: RoomStructuredPreferenceSyncStore(
                    context = context.applicationContext,
                    database = NewPipeDatabase.getInstance(context),
                    localPeerId = AndroidSyncStateRepository(context)
                        .loadOrCreateIdentity()
                        .peerId
                        .toBase58(),
                    onAiSListEnabledChanged = { enabled ->
                        AiSListSyncWorker.setEnabled(context.applicationContext, enabled)
                    }
                ).also { instance = it }
            }
        }
    }
}
