package org.schabi.newpipe.profiles

object ProfilePolicy {
    const val MAX_NAME_LENGTH = 40
    const val MAX_DESCRIPTION_LENGTH = 120

    @JvmStatic
    fun normalizeName(value: String?): String {
        return value.orEmpty().trim().replace(Regex("\\s+"), " ")
            .take(MAX_NAME_LENGTH)
    }

    @JvmStatic
    fun normalizeDescription(value: String?): String {
        return value.orEmpty().trim().take(MAX_DESCRIPTION_LENGTH)
    }

    @JvmStatic
    fun isUsableName(value: String?): Boolean = normalizeName(value).isNotEmpty()

    @JvmStatic
    fun isDuplicateName(
        requestedName: String?,
        profiles: Collection<ProfileRecord>,
        excludingId: String? = null
    ): Boolean {
        val normalized = normalizeName(requestedName)
        if (normalized.isEmpty()) {
            return false
        }
        return profiles.any {
            it.id != excludingId &&
                normalizeName(it.name).equals(normalized, ignoreCase = true)
        }
    }
}
