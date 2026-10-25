package org.schabi.newpipe.profiles

import android.content.Context
import android.os.Build
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.schabi.newpipe.extractor.ServiceList

/** Device-local restrictions and parent authentication; never included in profile sync. */
object KidsMode {
    internal const val PREFERENCES = "wizestream_kids_mode"
    private const val PROFILES = "profiles"
    private const val PIN_HASH = "pin_hash"
    private const val PIN_SALT = "pin_salt"
    private const val PIN_ALGORITHM = "pin_algorithm"
    private const val FAILURES = "failures"
    private const val LOCKED_UNTIL = "locked_until"
    private const val ITERATIONS = 120_000

    @Volatile
    private var parentAccess = false

    @JvmStatic
    fun isEnabled(context: Context): Boolean = isEnabled(context, ProfileManager.getActiveProfileId(context))

    @JvmStatic
    fun isEnabled(context: Context, profileId: String): Boolean = preferences(context).getStringSet(PROFILES, emptySet()).orEmpty().contains(profileId)

    @JvmStatic
    fun isSearchAllowed(context: Context, serviceId: Int): Boolean = serviceId != ServiceList.YouTube.serviceId || !isEnabled(context)

    @JvmStatic
    fun isSubscriptionAllowed(context: Context, serviceId: Int): Boolean = isSearchAllowed(context, serviceId)

    @JvmStatic
    fun requiresParentAccess(context: Context): Boolean = isEnabled(context) && !parentAccess

    @JvmStatic
    fun lock() {
        parentAccess = false
    }

    internal fun grantParentAccess() {
        parentAccess = true
    }

    @JvmStatic
    fun hasPin(context: Context): Boolean = preferences(context).contains(PIN_HASH)

    @JvmStatic
    fun setEnabled(context: Context, profileId: String, enabled: Boolean): Boolean {
        if (!parentAccess || !hasPin(context) || ProfileManager.getProfile(context, profileId) == null) return false
        val prefs = preferences(context)
        val profiles = prefs.getStringSet(PROFILES, emptySet()).orEmpty().toMutableSet()
        if (enabled) profiles.add(profileId) else profiles.remove(profileId)
        prefs.edit().putStringSet(PROFILES, profiles).apply()
        return true
    }

    internal fun forgetProfile(context: Context, profileId: String) {
        val prefs = preferences(context)
        val profiles = prefs.getStringSet(PROFILES, emptySet()).orEmpty().toMutableSet()
        if (profiles.remove(profileId)) prefs.edit().putStringSet(PROFILES, profiles).apply()
    }

    internal fun isValidPin(pin: String): Boolean = pin.matches(Regex("[0-9]{4,8}"))

    // Call on a worker thread. Replacing an existing PIN requires prior authentication.
    internal fun setPin(context: Context, pin: String): Boolean {
        if (!isValidPin(pin) || (hasPin(context) && !parentAccess)) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val algorithm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            "PBKDF2WithHmacSHA256"
        } else {
            "PBKDF2WithHmacSHA1"
        }
        val hash = derive(pin, salt, algorithm)
        return preferences(context).edit()
            .putString(PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putString(PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(PIN_ALGORITHM, algorithm)
            .remove(FAILURES)
            .remove(LOCKED_UNTIL)
            .commit()
    }

    internal fun verifyPin(context: Context, pin: String, now: Long = System.currentTimeMillis()): Boolean {
        val prefs = preferences(context)
        if (!hasPin(context) || retryAfterMillis(context, now) > 0) return false
        val matches = runCatching {
            val salt = Base64.decode(prefs.getString(PIN_SALT, ""), Base64.NO_WRAP)
            val expected = Base64.decode(prefs.getString(PIN_HASH, ""), Base64.NO_WRAP)
            val algorithm = prefs.getString(PIN_ALGORITHM, "").orEmpty()
            isValidPin(pin) && salt.size == 16 && expected.size == 32 &&
                algorithm in setOf("PBKDF2WithHmacSHA256", "PBKDF2WithHmacSHA1") &&
                MessageDigest.isEqual(expected, derive(pin, salt, algorithm))
        }.getOrDefault(false)
        val failures = if (matches) 0 else prefs.getInt(FAILURES, 0) + 1
        prefs.edit().putInt(FAILURES, failures % 5)
            .putLong(LOCKED_UNTIL, if (failures >= 5) now + 30_000 else 0)
            .commit()
        return matches
    }

    internal fun retryAfterMillis(context: Context, now: Long = System.currentTimeMillis()): Long = (preferences(context).getLong(LOCKED_UNTIL, 0) - now).coerceAtLeast(0)

    private fun derive(pin: String, salt: ByteArray, algorithm: String): ByteArray {
        val password = pin.toCharArray()
        val specification = PBEKeySpec(password, salt, ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance(algorithm).generateSecret(specification).encoded
        } finally {
            password.fill('\u0000')
            specification.clearPassword()
        }
    }

    private fun preferences(context: Context) = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
