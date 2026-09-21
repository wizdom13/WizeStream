/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import android.content.Context
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.profiles.ProfileSyncMergeResult

internal interface ProfileSyncStore {
    fun snapshot(): List<SyncedProfile>

    fun apply(profiles: List<SyncedProfile>): ProfileSyncApplyResult
}

internal class AndroidProfileSyncStore(context: Context) : ProfileSyncStore {
    private val appContext = context.applicationContext

    override fun snapshot(): List<SyncedProfile> {
        return ProfileManager.getProfiles(appContext).map { profile ->
            SyncedProfile(
                id = profile.id,
                name = profile.name,
                description = profile.description,
                iconKey = profile.iconKey,
                createdAtEpochMillis = profile.createdAt,
                updatedAtEpochMillis = ProfileManager.getProfileSyncUpdatedAt(
                    appContext,
                    profile.id
                )
            )
        }
    }

    override fun apply(profiles: List<SyncedProfile>): ProfileSyncApplyResult {
        var added = 0
        var updated = 0
        profiles.forEach { profile ->
            when (
                ProfileManager.upsertSyncedProfile(
                    appContext,
                    profile.id,
                    profile.name,
                    profile.description,
                    profile.iconKey,
                    profile.createdAtEpochMillis,
                    profile.updatedAtEpochMillis
                )
            ) {
                ProfileSyncMergeResult.ADDED -> added += 1
                ProfileSyncMergeResult.UPDATED -> updated += 1
                ProfileSyncMergeResult.UNCHANGED,
                ProfileSyncMergeResult.TOMBSTONED -> Unit
                ProfileSyncMergeResult.INVALID ->
                    throw ProfileSyncException("A synchronized profile could not be applied")
            }
        }
        return ProfileSyncApplyResult(added, updated)
    }
}
