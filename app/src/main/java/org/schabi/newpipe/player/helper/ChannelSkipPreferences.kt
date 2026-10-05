package org.schabi.newpipe.player.helper

import android.content.Context
import androidx.preference.PreferenceManager
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.profiles.ProfileManager

object ChannelSkipPreferences {
    const val MAX_SECONDS = 3600

    @JvmStatic
    fun isAvailable(info: StreamInfo): Boolean = !info.uploaderUrl.isNullOrBlank()

    internal fun key(profile: String, service: Int, uploader: String): String? = ChannelPlaybackProfileManager.profileKey(service, uploader)?.let {
        "channel_skip.v1.$profile." + it.removePrefix("channel_playback_profile.v1.")
    }

    @JvmStatic
    fun startSeconds(context: Context, info: StreamInfo): Int = read(context, info, ".start")

    @JvmStatic
    fun endSeconds(context: Context, info: StreamInfo): Int = read(context, info, ".end")

    @JvmStatic
    fun save(context: Context, info: StreamInfo, start: Int, end: Int) {
        require(start in 0..MAX_SECONDS && end in 0..MAX_SECONDS)
        val key = key(ProfileManager.getActiveProfileId(context), info.serviceId, info.uploaderUrl ?: return)
            ?: return
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putInt(key + ".start", start).putInt(key + ".end", end).apply()
    }

    private fun read(context: Context, info: StreamInfo, suffix: String): Int {
        val key = key(ProfileManager.getActiveProfileId(context), info.serviceId, info.uploaderUrl ?: return 0)
            ?: return 0
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getInt(key + suffix, 0).coerceIn(0, MAX_SECONDS)
    }
}
