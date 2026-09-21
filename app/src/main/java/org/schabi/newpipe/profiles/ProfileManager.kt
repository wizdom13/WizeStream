package org.schabi.newpipe.profiles

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import java.util.UUID
import org.schabi.newpipe.R

object ProfileManager {
    const val DEFAULT_PROFILE_ID = "00000000-0000-0000-0000-000000000000"

    private const val PROFILE_IDS_KEY = "wizestream_profile_ids"
    const val ACTIVE_PROFILE_ID_PREFERENCE_KEY = "wizestream_active_profile_id"
    private const val PROFILE_PREFIX = "wizestream_profile_"
    private const val NAME_SUFFIX = "_name"
    private const val DESCRIPTION_SUFFIX = "_description"
    private const val ICON_SUFFIX = "_icon"
    private const val CREATED_AT_SUFFIX = "_created_at"
    private const val UPDATED_AT_SUFFIX = "_updated_at"
    private const val SYNC_TOMBSTONES_KEY = "wizestream_profile_sync_tombstones"

    private val lock = Any()

    @JvmStatic
    fun getProfiles(context: Context): List<ProfileRecord> = synchronized(lock) {
        val prefs = preferences(context)
        ensureInitialized(context, prefs)
        profileIds(prefs)
            .mapNotNull { readProfile(prefs, it) }
            .sortedWith(
                compareBy<ProfileRecord> { it.id != DEFAULT_PROFILE_ID }
                    .thenBy { it.createdAt }
                    .thenBy { it.name.lowercase() }
            )
    }

    @JvmStatic
    fun getProfile(context: Context, profileId: String): ProfileRecord? = synchronized(lock) {
        val prefs = preferences(context)
        ensureInitialized(context, prefs)
        readProfile(prefs, profileId)
    }

    @JvmStatic
    fun getActiveProfileId(context: Context): String = synchronized(lock) {
        val prefs = preferences(context)
        ensureInitialized(context, prefs)
        val active = prefs.getString(ACTIVE_PROFILE_ID_PREFERENCE_KEY, DEFAULT_PROFILE_ID)
            ?: DEFAULT_PROFILE_ID
        if (profileIds(prefs).contains(active)) {
            active
        } else {
            prefs.edit().putString(ACTIVE_PROFILE_ID_PREFERENCE_KEY, DEFAULT_PROFILE_ID).apply()
            DEFAULT_PROFILE_ID
        }
    }

    @JvmStatic
    fun getActiveProfile(context: Context): ProfileRecord = synchronized(lock) {
        val prefs = preferences(context)
        ensureInitialized(context, prefs)
        val activeId = prefs.getString(ACTIVE_PROFILE_ID_PREFERENCE_KEY, DEFAULT_PROFILE_ID)
            ?: DEFAULT_PROFILE_ID
        readProfile(prefs, activeId)
            ?: readProfile(prefs, DEFAULT_PROFILE_ID)
            ?: defaultProfile()
    }

    @JvmStatic
    fun getDisplayName(context: Context, profile: ProfileRecord): String {
        return if (profile.id == DEFAULT_PROFILE_ID && profile.name.isBlank()) {
            context.getString(R.string.profile_default_name)
        } else {
            profile.name
        }
    }

    @JvmStatic
    fun setActiveProfile(context: Context, profileId: String): Boolean = synchronized(lock) {
        val prefs = preferences(context)
        ensureInitialized(context, prefs)
        if (!profileIds(prefs).contains(profileId)) {
            return false
        }
        prefs.edit().putString(ACTIVE_PROFILE_ID_PREFERENCE_KEY, profileId).apply()
        true
    }

    @JvmStatic
    fun createProfile(
        context: Context,
        name: String,
        description: String = "",
        iconKey: String = ProfileIcon.PERSON.key
    ): ProfileRecord? = synchronized(lock) {
        val normalizedName = ProfilePolicy.normalizeName(name)
        if (!ProfilePolicy.isUsableName(normalizedName)) {
            return null
        }

        val prefs = preferences(context)
        ensureInitialized(context, prefs)
        val existing = profileIds(prefs).mapNotNull { readProfile(prefs, it) }
        if (ProfilePolicy.isDuplicateName(normalizedName, existing)) {
            return null
        }

        val now = System.currentTimeMillis()
        val profile = ProfileRecord(
            id = UUID.randomUUID().toString(),
            name = normalizedName,
            description = ProfilePolicy.normalizeDescription(description),
            iconKey = ProfileIcon.fromKey(iconKey).key,
            createdAt = now
        )
        val ids = profileIds(prefs).toMutableSet().apply { add(profile.id) }
        prefs.edit()
            .putStringSet(PROFILE_IDS_KEY, ids)
            .putString(profileKey(profile.id, NAME_SUFFIX), profile.name)
            .putString(profileKey(profile.id, DESCRIPTION_SUFFIX), profile.description)
            .putString(profileKey(profile.id, ICON_SUFFIX), profile.iconKey)
            .putLong(profileKey(profile.id, CREATED_AT_SUFFIX), profile.createdAt)
            .putLong(profileKey(profile.id, UPDATED_AT_SUFFIX), now)
            .putString(ACTIVE_PROFILE_ID_PREFERENCE_KEY, profile.id)
            .apply()
        profile
    }

    @JvmStatic
    fun updateProfile(
        context: Context,
        profileId: String,
        name: String,
        description: String,
        iconKey: String
    ): Boolean = synchronized(lock) {
        val normalizedName = ProfilePolicy.normalizeName(name)
        if (!ProfilePolicy.isUsableName(normalizedName)) {
            return false
        }

        val prefs = preferences(context)
        ensureInitialized(context, prefs)
        val current = readProfile(prefs, profileId) ?: return false
        val existing = profileIds(prefs).mapNotNull { readProfile(prefs, it) }
        if (ProfilePolicy.isDuplicateName(normalizedName, existing, profileId)) {
            return false
        }

        prefs.edit()
            .putString(profileKey(profileId, NAME_SUFFIX), normalizedName)
            .putString(
                profileKey(profileId, DESCRIPTION_SUFFIX),
                ProfilePolicy.normalizeDescription(description)
            )
            .putString(profileKey(profileId, ICON_SUFFIX), ProfileIcon.fromKey(iconKey).key)
            .putLong(profileKey(profileId, CREATED_AT_SUFFIX), current.createdAt)
            .putLong(profileKey(profileId, UPDATED_AT_SUFFIX), System.currentTimeMillis())
            .apply()
        true
    }

    @JvmStatic
    fun deleteProfile(context: Context, profileId: String): Boolean = synchronized(lock) {
        if (profileId == DEFAULT_PROFILE_ID) {
            return false
        }

        val prefs = preferences(context)
        ensureInitialized(context, prefs)
        val ids = profileIds(prefs).toMutableSet()
        if (!ids.remove(profileId)) {
            return false
        }

        val tombstones = syncTombstones(prefs).toMutableSet().apply { add(profileId) }
        val editor = prefs.edit()
            .putStringSet(PROFILE_IDS_KEY, ids)
            .putStringSet(SYNC_TOMBSTONES_KEY, tombstones)
            .remove(profileKey(profileId, NAME_SUFFIX))
            .remove(profileKey(profileId, DESCRIPTION_SUFFIX))
            .remove(profileKey(profileId, ICON_SUFFIX))
            .remove(profileKey(profileId, CREATED_AT_SUFFIX))
            .remove(profileKey(profileId, UPDATED_AT_SUFFIX))
        if (prefs.getString(ACTIVE_PROFILE_ID_PREFERENCE_KEY, DEFAULT_PROFILE_ID) == profileId) {
            editor.putString(ACTIVE_PROFILE_ID_PREFERENCE_KEY, DEFAULT_PROFILE_ID)
        }
        editor.apply()
        true
    }

    @JvmStatic
    fun getProfileSyncUpdatedAt(context: Context, profileId: String): Long = synchronized(lock) {
        val prefs = preferences(context)
        ensureInitialized(context, prefs)
        val profile = readProfile(prefs, profileId) ?: return 0L
        prefs.getLong(profileKey(profileId, UPDATED_AT_SUFFIX), profile.createdAt)
    }

    @JvmStatic
    fun isProfileSyncTombstoned(
        context: Context,
        profileId: String
    ): Boolean = synchronized(lock) {
        if (profileId == DEFAULT_PROFILE_ID) {
            return false
        }
        val prefs = preferences(context)
        ensureInitialized(context, prefs)
        syncTombstones(prefs).contains(profileId)
    }

    @JvmStatic
    fun upsertSyncedProfile(
        context: Context,
        profileId: String,
        name: String,
        description: String,
        iconKey: String,
        createdAt: Long,
        updatedAt: Long
    ): ProfileSyncMergeResult = synchronized(lock) {
        if (!isCanonicalProfileId(profileId) ||
            createdAt !in 0..MAX_PROFILE_EPOCH_MILLIS ||
            updatedAt !in createdAt..MAX_PROFILE_EPOCH_MILLIS
        ) {
            return ProfileSyncMergeResult.INVALID
        }

        val prefs = preferences(context)
        ensureInitialized(context, prefs)
        if (profileId != DEFAULT_PROFILE_ID && syncTombstones(prefs).contains(profileId)) {
            return ProfileSyncMergeResult.TOMBSTONED
        }

        val normalizedName = ProfilePolicy.normalizeName(name)
        if (profileId != DEFAULT_PROFILE_ID && !ProfilePolicy.isUsableName(normalizedName)) {
            return ProfileSyncMergeResult.INVALID
        }
        val normalizedDescription = ProfilePolicy.normalizeDescription(description)
        val normalizedIcon = ProfileIcon.fromKey(iconKey).key
        val current = readProfile(prefs, profileId)

        if (current != null) {
            val currentUpdatedAt = prefs.getLong(
                profileKey(profileId, UPDATED_AT_SUFFIX),
                current.createdAt
            )
            val remoteMetadata = profileMetadataKey(
                normalizedName,
                normalizedDescription,
                normalizedIcon
            )
            val currentMetadata = profileMetadataKey(
                current.name,
                current.description,
                current.iconKey
            )
            if (updatedAt < currentUpdatedAt ||
                (updatedAt == currentUpdatedAt && remoteMetadata <= currentMetadata)
            ) {
                return ProfileSyncMergeResult.UNCHANGED
            }
        }

        val existingProfiles = profileIds(prefs).mapNotNull { readProfile(prefs, it) }
        val resolvedName = if (profileId == DEFAULT_PROFILE_ID && normalizedName.isBlank()) {
            ""
        } else {
            uniqueSyncedName(normalizedName, profileId, existingProfiles)
        }
        val ids = profileIds(prefs).toMutableSet().apply { add(profileId) }
        val resolvedCreatedAt = current?.createdAt?.let { minOf(it, createdAt) } ?: createdAt
        prefs.edit()
            .putStringSet(PROFILE_IDS_KEY, ids)
            .putString(profileKey(profileId, NAME_SUFFIX), resolvedName)
            .putString(profileKey(profileId, DESCRIPTION_SUFFIX), normalizedDescription)
            .putString(profileKey(profileId, ICON_SUFFIX), normalizedIcon)
            .putLong(profileKey(profileId, CREATED_AT_SUFFIX), resolvedCreatedAt)
            .putLong(profileKey(profileId, UPDATED_AT_SUFFIX), updatedAt)
            .apply()

        if (current == null) {
            ProfileSyncMergeResult.ADDED
        } else {
            ProfileSyncMergeResult.UPDATED
        }
    }

    private fun preferences(context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    }

    private fun ensureInitialized(context: Context, prefs: SharedPreferences) {
        val ids = profileIds(prefs).toMutableSet()
        val editor = prefs.edit()
        var changed = false

        if (ids.add(DEFAULT_PROFILE_ID)) {
            changed = true
        }
        if (!prefs.contains(profileKey(DEFAULT_PROFILE_ID, CREATED_AT_SUFFIX))) {
            editor
                .putString(profileKey(DEFAULT_PROFILE_ID, NAME_SUFFIX), "")
                .putString(profileKey(DEFAULT_PROFILE_ID, DESCRIPTION_SUFFIX), "")
                .putString(
                    profileKey(DEFAULT_PROFILE_ID, ICON_SUFFIX),
                    ProfileIcon.PERSON.key
                )
                .putLong(profileKey(DEFAULT_PROFILE_ID, CREATED_AT_SUFFIX), 0L)
                .putLong(profileKey(DEFAULT_PROFILE_ID, UPDATED_AT_SUFFIX), 0L)
            changed = true
        }

        ids.forEach { profileId ->
            if (!prefs.contains(profileKey(profileId, UPDATED_AT_SUFFIX))) {
                editor.putLong(
                    profileKey(profileId, UPDATED_AT_SUFFIX),
                    prefs.getLong(profileKey(profileId, CREATED_AT_SUFFIX), 0L)
                )
                changed = true
            }
        }

        val active = prefs.getString(ACTIVE_PROFILE_ID_PREFERENCE_KEY, null)
        if (active == null || !ids.contains(active)) {
            editor.putString(ACTIVE_PROFILE_ID_PREFERENCE_KEY, DEFAULT_PROFILE_ID)
            changed = true
        }

        if (changed || prefs.getStringSet(PROFILE_IDS_KEY, null) == null) {
            editor.putStringSet(PROFILE_IDS_KEY, ids)
            editor.apply()
        }

        // Force resolution once so the Default profile always has a localized display fallback.
        context.getString(R.string.profile_default_name)
    }

    private fun profileIds(prefs: SharedPreferences): Set<String> {
        return prefs.getStringSet(PROFILE_IDS_KEY, emptySet()).orEmpty().toSet()
    }

    private fun syncTombstones(prefs: SharedPreferences): Set<String> {
        return prefs.getStringSet(SYNC_TOMBSTONES_KEY, emptySet()).orEmpty().toSet()
    }

    private fun isCanonicalProfileId(profileId: String): Boolean {
        return runCatching { UUID.fromString(profileId).toString() == profileId }
            .getOrDefault(false)
    }

    private fun uniqueSyncedName(
        requestedName: String,
        profileId: String,
        profiles: Collection<ProfileRecord>
    ): String {
        if (!ProfilePolicy.isDuplicateName(requestedName, profiles, profileId)) {
            return requestedName
        }
        val suffixLengths = listOf(8, 12, 16, 32)
        suffixLengths.forEach { length ->
            val suffix = " (${profileId.replace("-", "").take(length)})"
            val base = requestedName.take(
                (ProfilePolicy.MAX_NAME_LENGTH - suffix.length).coerceAtLeast(1)
            )
            val candidate = ProfilePolicy.normalizeName(base + suffix)
            if (!ProfilePolicy.isDuplicateName(candidate, profiles, profileId)) {
                return candidate
            }
        }
        return profileId.take(ProfilePolicy.MAX_NAME_LENGTH)
    }

    private fun profileMetadataKey(name: String, description: String, iconKey: String): String {
        return listOf(
            ProfilePolicy.normalizeName(name),
            ProfilePolicy.normalizeDescription(description),
            ProfileIcon.fromKey(iconKey).key
        ).joinToString("\u0000")
    }

    private fun readProfile(prefs: SharedPreferences, profileId: String): ProfileRecord? {
        if (!profileIds(prefs).contains(profileId)) {
            return null
        }
        return ProfileRecord(
            id = profileId,
            name = prefs.getString(profileKey(profileId, NAME_SUFFIX), "").orEmpty(),
            description = prefs.getString(
                profileKey(profileId, DESCRIPTION_SUFFIX),
                ""
            ).orEmpty(),
            iconKey = ProfileIcon.fromKey(
                prefs.getString(profileKey(profileId, ICON_SUFFIX), null)
            ).key,
            createdAt = prefs.getLong(profileKey(profileId, CREATED_AT_SUFFIX), 0L)
        )
    }

    private fun defaultProfile(): ProfileRecord {
        return ProfileRecord(
            id = DEFAULT_PROFILE_ID,
            name = "",
            description = "",
            iconKey = ProfileIcon.PERSON.key,
            createdAt = 0L
        )
    }

    private fun profileKey(profileId: String, suffix: String): String {
        return PROFILE_PREFIX + profileId + suffix
    }

    private const val MAX_PROFILE_EPOCH_MILLIS = 253_402_300_799_999L
}

enum class ProfileSyncMergeResult {
    ADDED,
    UPDATED,
    UNCHANGED,
    TOMBSTONED,
    INVALID
}
