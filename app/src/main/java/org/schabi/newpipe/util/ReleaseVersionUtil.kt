package org.schabi.newpipe.util

import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.schabi.newpipe.App
import org.schabi.newpipe.BuildConfig

object ReleaseVersionUtil {
    /**
     * NewPipe Material is signed with the fork's own release key configured by the
     * NEWPIPE_MATERIAL_* release workflow secrets, so the upstream TeamNewPipe
     * certificate hash cannot be used to decide whether this APK is a release build.
     *
     * Gate release-only update UI and consent prompts on the actual fork release build
     * identity instead: non-debug builds using the release application id.
     */
    val isReleaseApk by lazy {
        val app = App.instance
        !BuildConfig.DEBUG && app.packageName == BuildConfig.APPLICATION_ID
    }

    fun isLastUpdateCheckExpired(expiry: Long): Boolean {
        return Instant.ofEpochSecond(expiry) < Instant.now()
    }

    /**
     * Coerce expiry date time in between 6 hours and 72 hours from now
     *
     * @return Epoch second of expiry date time
     */
    fun coerceUpdateCheckExpiry(expiryString: String?): Long {
        val nowPlus6Hours = ZonedDateTime.now().plusHours(6)
        val expiry = expiryString?.let {
            ZonedDateTime.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(it))
                .coerceIn(nowPlus6Hours, nowPlus6Hours.plusHours(66))
        } ?: nowPlus6Hours
        return expiry.toEpochSecond()
    }
}
