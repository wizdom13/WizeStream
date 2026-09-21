/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

class ProfileSyncEngine internal constructor(
    private val store: ProfileSyncStore
) {
    fun createRequest(): ProfileSyncRequest {
        return ProfileSyncRequest(
            profiles = store.snapshot()
        ).also(ProfileSyncValidation::validateRequest)
    }

    fun handleRequest(request: ProfileSyncRequest): ProfileSyncResponse {
        return try {
            ProfileSyncValidation.validateRequest(request)
            store.apply(request.profiles)
            ProfileSyncResponse(
                accepted = true,
                profiles = store.snapshot()
            ).also(ProfileSyncValidation::validateResponse)
        } catch (error: Exception) {
            ProfileSyncResponse(
                accepted = false,
                error = (
                    error.message ?: "The synchronized profile catalog was rejected"
                    ).take(MAX_RESPONSE_ERROR_LENGTH)
            )
        }
    }

    fun handleResponse(response: ProfileSyncResponse): ProfileSyncApplyResult {
        ProfileSyncValidation.validateResponse(response)
        if (!response.accepted) {
            throw ProfileSyncException(
                response.error ?: "The remote device rejected profile synchronization"
            )
        }
        return store.apply(response.profiles)
    }

    companion object {
        private const val MAX_RESPONSE_ERROR_LENGTH = 512
    }
}
