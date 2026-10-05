package org.schabi.newpipe.learning

/** Portable identity shared by backup storage and device synchronization. */
internal object LearningDifficultyIdentity {
    private val pattern = Regex(
        "learning_difficulty_rating\\.v1\\.[0-9a-f]{8}-[0-9a-f]{4}-" +
            "[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}:(0|[1-9][0-9]{0,9}):[0-9a-f]{64}"
    )

    fun isValid(key: String): Boolean = pattern.matches(key) &&
        key.substringAfter(':').substringBefore(':').toIntOrNull() != null

    fun profileId(key: String): String = key.removePrefix(LearningDifficulty.PREFIX)
        .substringBefore(':')
}
