package org.schabi.newpipe.player.helper;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Stores playback choices using the service and uploader URL as a stable channel identity.
 */
public final class ChannelPlaybackProfileManager {
    private static final String SPEED_SUFFIX = ".speed";
    private static final String QUALITY_SUFFIX = ".quality";
    private static final String CAPTION_SUFFIX = ".caption";
    private static final String CAPTION_DISABLED = "";

    private ChannelPlaybackProfileManager() {
    }

    public static boolean isEnabled(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                context.getString(R.string.per_channel_playback_profiles_key), true);
    }

    public static boolean isAvailable(@NonNull final Context context,
                                      @NonNull final PlayQueueItem item) {
        return isEnabled(context) && profileKey(item.getServiceId(), item.getUploaderUrl()) != null;
    }

    public static boolean isAvailable(@NonNull final Context context,
                                      @NonNull final StreamInfo info) {
        return isEnabled(context) && profileKey(info.getServiceId(), info.getUploaderUrl()) != null;
    }

    @Nullable
    public static Float getSpeed(@NonNull final Context context,
                                 @NonNull final PlayQueueItem item) {
        return getSpeed(context, item.getServiceId(), item.getUploaderUrl());
    }

    @Nullable
    public static Float getSpeed(@NonNull final Context context,
                                 @NonNull final StreamInfo info) {
        return getSpeed(context, info.getServiceId(), info.getUploaderUrl());
    }

    @Nullable
    public static String getQuality(@NonNull final Context context,
                                    @NonNull final StreamInfo info) {
        final String key = enabledProfileKey(context, info.getServiceId(), info.getUploaderUrl());
        return key == null ? null : preferences(context).getString(key + QUALITY_SUFFIX, null);
    }

    public static boolean hasCaptionPreference(@NonNull final Context context,
                                               @NonNull final StreamInfo info) {
        final String key = enabledProfileKey(context, info.getServiceId(), info.getUploaderUrl());
        return key != null && preferences(context).contains(key + CAPTION_SUFFIX);
    }

    @Nullable
    public static String getCaptionPreference(@NonNull final Context context,
                                              @NonNull final StreamInfo info) {
        final String key = enabledProfileKey(context, info.getServiceId(), info.getUploaderUrl());
        if (key == null) {
            return null;
        }

        final String preference = preferences(context).getString(key + CAPTION_SUFFIX, null);
        return CAPTION_DISABLED.equals(preference) ? null : preference;
    }

    public static boolean saveSpeed(@NonNull final Context context,
                                    @Nullable final StreamInfo info,
                                    @Nullable final PlayQueueItem item,
                                    final float speed) {
        final String key = enabledProfileKey(context, info, item);
        if (key == null) {
            return false;
        }

        preferences(context).edit().putFloat(key + SPEED_SUFFIX, speed).apply();
        return true;
    }

    public static boolean saveQuality(@NonNull final Context context,
                                      @Nullable final StreamInfo info,
                                      @Nullable final PlayQueueItem item,
                                      @NonNull final String quality) {
        final String key = enabledProfileKey(context, info, item);
        if (key == null) {
            return false;
        }

        preferences(context).edit().putString(key + QUALITY_SUFFIX, quality).apply();
        return true;
    }

    public static boolean saveCaptionPreference(@NonNull final Context context,
                                                @Nullable final StreamInfo info,
                                                @Nullable final PlayQueueItem item,
                                                @Nullable final String language) {
        final String key = enabledProfileKey(context, info, item);
        if (key == null) {
            return false;
        }

        preferences(context).edit().putString(
                key + CAPTION_SUFFIX, language == null ? CAPTION_DISABLED : language).apply();
        return true;
    }

    @Nullable
    private static Float getSpeed(@NonNull final Context context,
                                  final int serviceId,
                                  @Nullable final String uploaderUrl) {
        final String key = enabledProfileKey(context, serviceId, uploaderUrl);
        if (key == null || !preferences(context).contains(key + SPEED_SUFFIX)) {
            return null;
        }
        return preferences(context).getFloat(key + SPEED_SUFFIX, 1.0f);
    }

    @Nullable
    private static String enabledProfileKey(@NonNull final Context context,
                                            @Nullable final StreamInfo info,
                                            @Nullable final PlayQueueItem item) {
        if (!isEnabled(context)) {
            return null;
        } else if (info != null) {
            return profileKey(info.getServiceId(), info.getUploaderUrl());
        } else if (item != null) {
            return profileKey(item.getServiceId(), item.getUploaderUrl());
        } else {
            return null;
        }
    }

    @Nullable
    private static String enabledProfileKey(@NonNull final Context context,
                                            final int serviceId,
                                            @Nullable final String uploaderUrl) {
        return isEnabled(context) ? profileKey(serviceId, uploaderUrl) : null;
    }

    @Nullable
    static String profileKey(final int serviceId, @Nullable final String uploaderUrl) {
        if (uploaderUrl == null || uploaderUrl.isBlank()) {
            return null;
        }

        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(uploaderUrl.getBytes(StandardCharsets.UTF_8));
            final StringBuilder key = new StringBuilder("channel_playback_profile.v1.")
                    .append(serviceId)
                    .append('.');
            for (final byte value : digest) {
                key.append(Character.forDigit((value >>> 4) & 0x0f, 16));
                key.append(Character.forDigit(value & 0x0f, 16));
            }
            return key.toString();
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    @NonNull
    private static SharedPreferences preferences(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }
}
