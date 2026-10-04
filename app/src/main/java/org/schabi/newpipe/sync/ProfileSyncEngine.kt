/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

class ProfileSyncEngine internal constructor(
    private val store: ProfileSyncStore
) {
    internal fun createRequest(): ProfileSyncRequest {
        val request = ProfileSyncRequest(profiles = store.snapshot())
        try {
            ProfileSyncValidation.validateRequest(request)
        } catch (error: ProfileSyncException) {
            throw ProfileSyncException("Local profile catalog: ${error.message}", error)
        }
        return request
    }

    internal fun handleRequest(request: ProfileSyncRequest): ProfileSyncResponse {
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

    internal fun handleResponse(response: ProfileSyncResponse): ProfileSyncApplyResult {
        ProfileSyncValidation.validateResponse(response)
        if (!response.accepted) {
            throw ProfileSyncException(
                "Remote device rejected profile synchronization: " +
                    (response.error ?: "No reason supplied")
            )
        }
        return store.apply(response.profiles)
    }

    companion object {
        private const val MAX_RESPONSE_ERROR_LENGTH = 512
    }
}
