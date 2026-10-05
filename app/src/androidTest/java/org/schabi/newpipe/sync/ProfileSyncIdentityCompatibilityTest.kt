/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.profiles.ProfileIcon
import org.schabi.newpipe.profiles.ProfileManager

@RunWith(AndroidJUnit4::class)
class ProfileSyncIdentityCompatibilityTest {
    @Test
    fun defaultProfileCatalogSurvivesWireRoundTrip() {
        val request = ProfileSyncRequest(
            profiles = listOf(
                SyncedProfile(
                    id = ProfileManager.DEFAULT_PROFILE_ID,
                    name = "",
                    iconKey = ProfileIcon.PERSON.key,
                    createdAtEpochMillis = 0,
                    updatedAtEpochMillis = 0
                )
            )
        )
        val decodedRequest = ProfileSyncCodec.decodeRequest(ProfileSyncCodec.encodeRequest(request))
        ProfileSyncValidation.validateRequest(decodedRequest)

        val response = ProfileSyncResponse(accepted = true, profiles = decodedRequest.profiles)
        val decodedResponse =
            ProfileSyncCodec.decodeResponse(ProfileSyncCodec.encodeResponse(response))
        ProfileSyncValidation.validateResponse(decodedResponse)
        assertEquals(ProfileManager.DEFAULT_PROFILE_ID, decodedResponse.profiles.single().id)
    }
}
