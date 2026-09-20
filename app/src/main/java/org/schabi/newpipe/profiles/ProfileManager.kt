package org.schabi.newpipe.profiles

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import java.util.UUID
import org.schabi.newpipe.R

object ProfileManager {
    const val DEFAULT_PROFILE_ID = "00000000-0000-0000-0000-000000000000"

    private const val PROFILE_IDS_KEY = "wizestream_profile_ids"
    private const val ACTIVE_PROFILE_ID_KEY = "wizestream_active_profile_id"
    private const val PROFILE_PREFIX = "wizestream_profile_"
    private const val NAME_SUFFIX = "_name"
    private const val DESCRIPTION_SUFFIX = "_description"
    private const val ICON_SUFFIX = "_icon"
    private const val CREATED_AT_SUFFIX = "_created_at"

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
        val active = prefs.getString(ACTIVE_PROFILE_ID_KEY, DEFAULT_PROFILE_ID)
            ?: DEFAULT_PROFILE_ID
        if (profileIds(prefs).contains(active)) {
            active
        } else {
            prefs.edit().putString(ACTIVE_PROFILE_ID_KEY, DEFAULT_PROFILE_ID).apply()
            DEFAULT_PROFILE_ID
        }
    }

    @JvmStatic
    fun getActiveProfile(context: Context): ProfileRecord = synchronized(lock) {
        val prefs = preferences(context)
        ensureInitialized(context, prefs)
        val activeId = prefs.getString(ACTIVE_PROFILE_ID_KEY, DEFAULT_PROFILE_ID)
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
        prefs.edit().putString(ACTIVE_PROFILE_ID_KEY, profileId).apply()
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

        val profile = ProfileRecord(
            id = UUID.randomUUID().toString(),
            name = normalizedName,
            description = ProfilePolicy.normalizeDescription(description),
            iconKey = ProfileIcon.fromKey(iconKey).key,
            createdAt = System.currentTimeMillis()
        )
        val ids = profileIds(prefs).toMutableSet().apply { add(profile.id) }
        prefs.edit()
            .putStringSet(PROFILE_IDS_KEY, ids)
            .putString(profileKey(profile.id, NAME_SUFFIX), profile.name)
            .putString(profileKey(profile.id, DESCRIPTION_SUFFIX), profile.description)
            .putString(profileKey(profile.id, ICON_SUFFIX), profile.iconKey)
            .putLong(profileKey(profile.id, CREATED_AT_SUFFIX), profile.createdAt)
            .putString(ACTIVE_PROFILE_ID_KEY, profile.id)
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

        val editor = prefs.edit()
            .putStringSet(PROFILE_IDS_KEY, ids)
            .remove(profileKey(profileId, NAME_SUFFIX))
            .remove(profileKey(profileId, DESCRIPTION_SUFFIX))
            .remove(profileKey(profileId, ICON_SUFFIX))
            .remove(profileKey(profileId, CREATED_AT_SUFFIX))
        if (prefs.getString(ACTIVE_PROFILE_ID_KEY, DEFAULT_PROFILE_ID) == profileId) {
            editor.putString(ACTIVE_PROFILE_ID_KEY, DEFAULT_PROFILE_ID)
        }
        editor.apply()
        true
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
            changed = true
        }

        val active = prefs.getString(ACTIVE_PROFILE_ID_KEY, null)
        if (active == null || !ids.contains(active)) {
            editor.putString(ACTIVE_PROFILE_ID_KEY, DEFAULT_PROFILE_ID)
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
}
