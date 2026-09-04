/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import java.util.UUID
import kotlinx.serialization.encodeToString
import org.schabi.newpipe.database.sync.StructuredPreferenceSyncRecordEntity
import org.schabi.newpipe.streams.io.StoredFileHelper
import us.shandian.giga.get.sqlite.FinishedMissionStore

internal class CompletedDownloadSyncAdapter(
    private val localPeerId: String,
    private val finishedMissionStore: FinishedMissionStore,
    private val recordRepository: StructuredPreferenceRecordRepository
) : StructuredPreferenceCategoryAdapter {
    override val category = StructuredPreferenceCategory.COMPLETED_DOWNLOADS

    override fun snapshotHash(): String {
        return structuredPreferenceDigest(
            STRUCTURED_PREFERENCE_JSON.encodeToString(currentCompletedDownloads())
        )
    }

    override fun reconcile(bootstrap: Boolean) {
        val desired = currentCompletedDownloads().associateBy(
            SyncedCompletedDownload::syncId
        )
        desired.forEach { (recordId, download) ->
            recordRepository.saveLocalUpsert(
                category = category,
                recordId = recordId,
                recordType = StructuredPreferenceRecordType.COMPLETED_DOWNLOAD,
                record = SyncedStructuredPreferenceRecord(completedDownload = download)
            )
        }
        recordRepository.getRecordsByType(
            category,
            StructuredPreferenceRecordType.COMPLETED_DOWNLOAD
        ).filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .filter { record ->
                recordRepository.decodeRecord(record).completedDownload
                    ?.ownerPeerId == localPeerId
            }
            .filterNot { it.recordId in desired }
            .forEach(recordRepository::saveLocalDelete)
    }

    override fun materialize() = Unit

    fun getMetadata(): List<SyncedCompletedDownload> {
        return recordRepository.getRecordsByType(
            category,
            StructuredPreferenceRecordType.COMPLETED_DOWNLOAD
        ).filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .mapNotNull { record ->
                recordRepository.decodeRecord(record).completedDownload
            }
            .sortedByDescending(SyncedCompletedDownload::completedAtEpochMillis)
    }

    private fun currentCompletedDownloads(): List<SyncedCompletedDownload> {
        return finishedMissionStore.loadCompletedDownloadMetadata().mapNotNull { mission ->
            val syncId = mission.syncId?.takeIf(::isCanonicalUuid)
                ?: return@mapNotNull null
            val sourceUrl = mission.source?.trim()
                ?.takeIf { it.isNotEmpty() && it.length <= MAX_STRUCTURED_URL_LENGTH }
                ?: return@mapNotNull null
            val displayName = mission.displayName?.trim()
                ?.take(MAX_DOWNLOAD_DISPLAY_NAME_LENGTH)
                ?.takeIf(String::isNotEmpty)
                ?: "download"
            val mimeType = mission.mimeType?.trim()
                ?.take(MAX_DOWNLOAD_MIME_TYPE_LENGTH)
                ?.takeIf(String::isNotEmpty)
                ?: StoredFileHelper.DEFAULT_MIME
            val mediaKind = mission.kind.toString()
                .takeIf { it[0] in SUPPORTED_LOCAL_DOWNLOAD_KINDS }
                ?: "?"
            SyncedCompletedDownload(
                syncId = syncId,
                ownerPeerId = localPeerId,
                sourceUrl = sourceUrl,
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = mission.length.coerceAtLeast(0),
                completedAtEpochMillis = mission.timestamp.coerceAtLeast(1),
                mediaKind = mediaKind
            )
        }.sortedBy(SyncedCompletedDownload::syncId)
    }

    private fun isCanonicalUuid(value: String): Boolean {
        return runCatching { UUID.fromString(value).toString() == value }
            .getOrDefault(false)
    }

    private companion object {
        val SUPPORTED_LOCAL_DOWNLOAD_KINDS = setOf('a', 'v', 's', '?')
    }
}
