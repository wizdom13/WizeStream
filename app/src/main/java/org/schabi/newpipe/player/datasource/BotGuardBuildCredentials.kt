package org.schabi.newpipe.player.datasource

import org.schabi.newpipe.BuildConfig

internal fun botGuardBuildCredentialsError(
    googleApiKey: String,
    requestKey: String
): String? {
    val missingCredentials = buildList {
        if (googleApiKey.isBlank()) {
            add("WIZESTREAM_BOTGUARD_GOOGLE_API_KEY")
        }
        if (requestKey.isBlank()) {
            add("WIZESTREAM_BOTGUARD_REQUEST_KEY")
        }
    }
    return if (missingCredentials.isEmpty()) {
        null
    } else {
        "BotGuard build credentials missing: configure " +
            missingCredentials.joinToString(" and ")
    }
}

internal fun botGuardBuildCredentialsError(): String? = botGuardBuildCredentialsError(
    BuildConfig.BOTGUARD_GOOGLE_API_KEY,
    BuildConfig.BOTGUARD_REQUEST_KEY
)
