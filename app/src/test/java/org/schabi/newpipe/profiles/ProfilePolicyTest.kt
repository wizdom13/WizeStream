package org.schabi.newpipe.profiles

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

import org.junit.Test

class ProfilePolicyTest {
    @Test
    fun normalizesProfileNames() {
        assertEquals("Work profile", ProfilePolicy.normalizeName("  Work   profile  "))
        assertFalse(ProfilePolicy.isUsableName("   "))
    }

    @Test
    fun detectsDuplicateProfileNamesCaseInsensitively() {
        val profiles = listOf(
            ProfileRecord("1", "Personal", "", "person", 1L),
            ProfileRecord("2", "Work", "", "work", 2L)
        )

        assertTrue(ProfilePolicy.isDuplicateName(" work ", profiles))
        assertFalse(ProfilePolicy.isDuplicateName("Study", profiles))
        assertFalse(ProfilePolicy.isDuplicateName("Work", profiles, "2"))
    }

    @Test
    fun boundsUserControlledFields() {
        assertEquals(
            ProfilePolicy.MAX_NAME_LENGTH,
            ProfilePolicy.normalizeName("x".repeat(100)).length
        )
        assertEquals(
            ProfilePolicy.MAX_DESCRIPTION_LENGTH,
            ProfilePolicy.normalizeDescription("y".repeat(200)).length
        )
    }
}
