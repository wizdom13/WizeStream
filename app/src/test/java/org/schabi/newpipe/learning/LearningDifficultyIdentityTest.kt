package org.schabi.newpipe.learning

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningDifficultyIdentityTest {
    @Test
    fun portableKeysRequireACanonicalProfileServiceAndDigest() {
        val prefix = "learning_difficulty_rating.v1.00000000-0000-0000-0000-000000000000:"
        val digest = "a".repeat(64)
        assertTrue(LearningDifficultyIdentity.isValid(prefix + "0:" + digest))
        assertFalse(LearningDifficultyIdentity.isValid(prefix + "-1:" + digest))
        assertFalse(LearningDifficultyIdentity.isValid(prefix + "00:" + digest))
        assertFalse(LearningDifficultyIdentity.isValid(prefix + "2147483648:" + digest))
        assertFalse(LearningDifficultyIdentity.isValid(prefix + "0:" + digest.uppercase()))
        assertFalse(LearningDifficultyIdentity.isValid(prefix + "0:" + digest.dropLast(1)))
        assertFalse(LearningDifficultyIdentity.isValid("arbitrary-preference-key"))
    }
}
