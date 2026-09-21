package org.schabi.newpipe.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.schabi.newpipe.profiles.ProfileIcon
import org.schabi.newpipe.profiles.ProfileManager

class ProfileSyncEngineTest {
    @Test
    fun exchangesCatalogAndAppliesRemoteProfiles() {
        val local = TestProfileSyncStore(
            mutableListOf(
                profile(ProfileManager.DEFAULT_PROFILE_ID, "", 0, 0),
                profile("11111111-1111-1111-1111-111111111111", "Work", 10, 20)
            )
        )
        val remote = TestProfileSyncStore(
            mutableListOf(
                profile(ProfileManager.DEFAULT_PROFILE_ID, "", 0, 0),
                profile("22222222-2222-2222-2222-222222222222", "Study", 10, 30)
            )
        )
        val localEngine = ProfileSyncEngine(local)
        val remoteEngine = ProfileSyncEngine(remote)

        val request = localEngine.createRequest()
        val response = remoteEngine.handleRequest(request)
        val applied = localEngine.handleResponse(response)

        assertEquals(1, applied.addedProfiles)
        assertEquals(
            setOf(
                ProfileManager.DEFAULT_PROFILE_ID,
                "11111111-1111-1111-1111-111111111111",
                "22222222-2222-2222-2222-222222222222"
            ),
            local.profiles.mapTo(mutableSetOf()) { it.id }
        )
        assertEquals(3, remote.profiles.size)
    }

    @Test
    fun rejectsMalformedOrDuplicateProfileIds() {
        val invalid = ProfileSyncRequest(
            profiles = listOf(
                profile(ProfileManager.DEFAULT_PROFILE_ID, "", 0, 0),
                profile("not-a-uuid", "Work", 10, 20)
            )
        )
        assertThrows(ProfileSyncException::class.java) {
            ProfileSyncValidation.validateRequest(invalid)
        }

        val duplicate = profile(
            "11111111-1111-1111-1111-111111111111",
            "Work",
            10,
            20
        )
        assertThrows(ProfileSyncException::class.java) {
            ProfileSyncValidation.validateProfiles(
                listOf(
                    profile(ProfileManager.DEFAULT_PROFILE_ID, "", 0, 0),
                    duplicate,
                    duplicate
                )
            )
        }
    }

    @Test
    fun rejectedResponseIsNotApplied() {
        val store = TestProfileSyncStore(
            mutableListOf(profile(ProfileManager.DEFAULT_PROFILE_ID, "", 0, 0))
        )
        val engine = ProfileSyncEngine(store)
        assertThrows(ProfileSyncException::class.java) {
            engine.handleResponse(
                ProfileSyncResponse(
                    accepted = false,
                    error = "rejected"
                )
            )
        }
        assertEquals(1, store.profiles.size)
        assertFalse(store.applied)
    }

    private fun profile(
        id: String,
        name: String,
        createdAt: Long,
        updatedAt: Long
    ) = SyncedProfile(
        id = id,
        name = name,
        description = "",
        iconKey = ProfileIcon.PERSON.key,
        createdAtEpochMillis = createdAt,
        updatedAtEpochMillis = updatedAt
    )
}

private class TestProfileSyncStore(
    val profiles: MutableList<SyncedProfile>
) : ProfileSyncStore {
    var applied = false

    override fun snapshot(): List<SyncedProfile> = profiles.toList()

    override fun apply(profiles: List<SyncedProfile>): ProfileSyncApplyResult {
        applied = true
        var added = 0
        var updated = 0
        profiles.forEach { incoming ->
            val index = this.profiles.indexOfFirst { it.id == incoming.id }
            if (index < 0) {
                this.profiles += incoming
                added += 1
            } else if (incoming.updatedAtEpochMillis > this.profiles[index].updatedAtEpochMillis) {
                this.profiles[index] = incoming
                updated += 1
            }
        }
        return ProfileSyncApplyResult(added, updated)
    }
}
