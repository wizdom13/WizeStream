/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import java.util.UUID
import kotlinx.serialization.Serializable
import org.schabi.newpipe.profiles.ProfileIcon
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.profiles.ProfilePolicy

internal const val PROFILE_SYNC_PROTOCOL_ID = "/wizestream/profiles/1.0.0"
internal const val PROFILE_SYNC_VERSION = 1
internal const val MAX_SYNCED_PROFILES = 64

@Serializable
internal data class SyncedProfile(
    val id: String,
    val name: String,
    val description: String = "",
    val iconKey: String = ProfileIcon.PERSON.key,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Serializable
internal data class ProfileSyncRequest(
    val version: Int = PROFILE_SYNC_VERSION,
    val profiles: List<SyncedProfile>
)

@Serializable
internal data class ProfileSyncResponse(
    val accepted: Boolean,
    val error: String? = null,
    val profiles: List<SyncedProfile> = emptyList()
)

data class ProfileSyncResult(
    val peer: TrustedPeer,
    val sentChanges: Int,
    val receivedChanges: Int,
    val addedProfiles: Int,
    val updatedProfiles: Int
)

internal data class ProfileSyncApplyResult(
    val addedProfiles: Int,
    val updatedProfiles: Int
)

internal object ProfileSyncValidation {
    fun validateRequest(request: ProfileSyncRequest) {
        if (request.version != PROFILE_SYNC_VERSION) {
            throw ProfileSyncException(
                "Unsupported profile synchronization version: ${request.version}"
            )
        }
        validateProfiles(request.profiles)
    }

    fun validateResponse(response: ProfileSyncResponse) {
        if (!response.accepted) {
            if (response.error.isNullOrBlank()) {
                throw ProfileSyncException("The remote device rejected profile synchronization")
            }
            if (response.error.length > MAX_SYNC_ERROR_LENGTH) {
                throw ProfileSyncException("The profile synchronization error is too large")
            }
            return
        }
        if (response.error != null) {
            throw ProfileSyncException(
                "A successful profile synchronization response has an error"
            )
        }
        validateProfiles(response.profiles)
    }

    fun validateProfiles(profiles: List<SyncedProfile>) {
        if (profiles.size !in 1..MAX_SYNCED_PROFILES) {
            throw ProfileSyncException("The profile catalog has an invalid size")
        }
        val ids = hashSetOf<String>()
        profiles.forEach { profile ->
            if (!ids.add(profile.id) || !isCanonicalUuid(profile.id)) {
                throw ProfileSyncException("A synchronized profile has an invalid identity")
            }
            val normalizedName = ProfilePolicy.normalizeName(profile.name)
            if (
                profile.id != ProfileManager.DEFAULT_PROFILE_ID &&
                !ProfilePolicy.isUsableName(normalizedName)
            ) {
                throw ProfileSyncException("A synchronized profile has no usable name")
            }
            if (
                profile.name != normalizedName ||
                profile.description !=
                ProfilePolicy.normalizeDescription(profile.description) ||
                ProfileIcon.entries.none { it.key == profile.iconKey } ||
                profile.createdAtEpochMillis !in 0..MAX_PROFILE_EPOCH_MILLIS ||
                profile.updatedAtEpochMillis !in
                profile.createdAtEpochMillis..MAX_PROFILE_EPOCH_MILLIS
            ) {
                throw ProfileSyncException("Synchronized profile metadata is invalid")
            }
        }
        if (profiles.none { it.id == ProfileManager.DEFAULT_PROFILE_ID }) {
            throw ProfileSyncException("The profile catalog is missing Default")
        }
    }

    private fun isCanonicalUuid(value: String): Boolean {
        return runCatching { UUID.fromString(value).toString() == value }
            .getOrDefault(false)
    }

    private const val MAX_SYNC_ERROR_LENGTH = 512
    private const val MAX_PROFILE_EPOCH_MILLIS = 253_402_300_799_999L
}

class ProfileSyncException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
