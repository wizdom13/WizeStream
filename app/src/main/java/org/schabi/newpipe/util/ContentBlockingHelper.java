package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.post.PostInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Stores and applies user-defined video, channel, and keyword blocking rules. */
public final class ContentBlockingHelper {
    private static final String ENTRY_SEPARATOR = "\t";

    private ContentBlockingHelper() {
    }

    @NonNull
    public static Rules getRules(@NonNull final Context context) {
        final SharedPreferences preferences = preferences(context);
        return Rules.create(
                preferences.getBoolean(context.getString(
                        R.string.content_blocking_enabled_key), true),
                copySet(preferences, context.getString(R.string.blocked_videos_key)),
                copySet(preferences, context.getString(R.string.blocked_channels_key)),
                preferences.getString(context.getString(
                        R.string.blocked_keywords_key), ""));
    }

    public static void blockVideo(@NonNull final Context context,
                                  @NonNull final StreamInfoItem item) {
        addEntry(context, R.string.blocked_videos_key, item.getUrl(), item.getName());
        enable(context);
    }

    public static void blockChannel(@NonNull final Context context,
                                    @Nullable final String channelUrl,
                                    @Nullable final String channelName) {
        final String key = !isBlank(channelUrl) ? channelUrl : channelName;
        if (isBlank(key)) {
            return;
        }
        addEntry(context, R.string.blocked_channels_key, key,
                isBlank(channelName) ? key : channelName);
        enable(context);
    }

    @NonNull
    public static List<Entry> getBlockedVideos(@NonNull final Context context) {
        return decodeEntries(copySet(preferences(context),
                context.getString(R.string.blocked_videos_key)));
    }

    @NonNull
    public static List<Entry> getBlockedChannels(@NonNull final Context context) {
        return decodeEntries(copySet(preferences(context),
                context.getString(R.string.blocked_channels_key)));
    }

    public static void saveBlockedVideos(@NonNull final Context context,
                                         @NonNull final List<Entry> entries) {
        saveEntries(context, R.string.blocked_videos_key, entries);
    }

    public static void saveBlockedChannels(@NonNull final Context context,
                                           @NonNull final List<Entry> entries) {
        saveEntries(context, R.string.blocked_channels_key, entries);
    }

    public static void clearAll(@NonNull final Context context) {
        preferences(context).edit()
                .remove(context.getString(R.string.blocked_videos_key))
                .remove(context.getString(R.string.blocked_channels_key))
                .remove(context.getString(R.string.blocked_keywords_key))
                .apply();
    }

    public static boolean isPreferenceKey(@NonNull final Context context,
                                          @Nullable final String key) {
        return context.getString(R.string.content_blocking_enabled_key).equals(key)
                || context.getString(R.string.blocked_videos_key).equals(key)
                || context.getString(R.string.blocked_channels_key).equals(key)
                || context.getString(R.string.blocked_keywords_key).equals(key);
    }

    private static void enable(@NonNull final Context context) {
        preferences(context).edit()
                .putBoolean(context.getString(R.string.content_blocking_enabled_key), true)
                .apply();
    }

    private static void addEntry(@NonNull final Context context,
                                 final int preferenceKey,
                                 @NonNull final String key,
                                 @Nullable final String label) {
        final SharedPreferences preferences = preferences(context);
        final String resolvedKey = context.getString(preferenceKey);
        final Set<String> entries = copySet(preferences, resolvedKey);
        entries.removeIf(value -> decodeEntry(value).key.equals(normalize(key)));
        entries.add(encodeEntry(key, label));
        preferences.edit().putStringSet(resolvedKey, entries).apply();
    }

    private static void saveEntries(@NonNull final Context context,
                                    final int preferenceKey,
                                    @NonNull final List<Entry> entries) {
        final Set<String> encoded = new HashSet<>();
        for (final Entry entry : entries) {
            encoded.add(encodeEntry(entry.key, entry.label));
        }
        preferences(context).edit()
                .putStringSet(context.getString(preferenceKey), encoded)
                .apply();
    }

    @NonNull
    private static SharedPreferences preferences(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    @NonNull
    private static Set<String> copySet(@NonNull final SharedPreferences preferences,
                                       @NonNull final String key) {
        return new HashSet<>(preferences.getStringSet(key, Collections.emptySet()));
    }

    @NonNull
    private static String encodeEntry(@NonNull final String key, @Nullable final String label) {
        final String safeLabel = isBlank(label) ? key : label.replace('\t', ' ');
        return normalize(key) + ENTRY_SEPARATOR + safeLabel.trim();
    }

    @NonNull
    private static List<Entry> decodeEntries(@NonNull final Set<String> values) {
        final List<Entry> entries = new ArrayList<>();
        for (final String value : values) {
            entries.add(decodeEntry(value));
        }
        entries.sort((first, second) -> first.label.compareToIgnoreCase(second.label));
        return entries;
    }

    @NonNull
    private static Entry decodeEntry(@NonNull final String value) {
        final int separator = value.indexOf(ENTRY_SEPARATOR);
        if (separator < 0) {
            return new Entry(normalize(value), value);
        }
        return new Entry(normalize(value.substring(0, separator)),
                value.substring(separator + ENTRY_SEPARATOR.length()));
    }

    private static boolean isBlank(@Nullable final String value) {
        return value == null || value.trim().isEmpty();
    }

    @NonNull
    private static String normalize(@Nullable final String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static final class Entry {
        @NonNull
        private final String key;
        @NonNull
        private final String label;

        private Entry(@NonNull final String key, @NonNull final String label) {
            this.key = key;
            this.label = label;
        }

        @NonNull
        public String getLabel() {
            return label;
        }
    }

    public static final class Rules {
        private final boolean enabled;
        @NonNull
        private final Set<String> blockedVideoUrls;
        @NonNull
        private final Set<String> blockedChannelKeys;
        @NonNull
        private final Set<String> blockedChannelNames;
        @NonNull
        private final List<String> blockedKeywords;

        private Rules(final boolean enabled,
                      @NonNull final Set<String> blockedVideoUrls,
                      @NonNull final Set<String> blockedChannelKeys,
                      @NonNull final Set<String> blockedChannelNames,
                      @NonNull final List<String> blockedKeywords) {
            this.enabled = enabled;
            this.blockedVideoUrls = blockedVideoUrls;
            this.blockedChannelKeys = blockedChannelKeys;
            this.blockedChannelNames = blockedChannelNames;
            this.blockedKeywords = blockedKeywords;
        }

        @NonNull
        static Rules create(final boolean enabled,
                            @NonNull final Set<String> videoEntries,
                            @NonNull final Set<String> channelEntries,
                            @Nullable final String keywords) {
            final Set<String> videos = new HashSet<>();
            for (final String value : videoEntries) {
                videos.add(decodeEntry(value).key);
            }

            final Set<String> channels = new HashSet<>();
            final Set<String> channelNames = new HashSet<>();
            for (final String value : channelEntries) {
                final Entry entry = decodeEntry(value);
                channels.add(entry.key);
                channelNames.add(normalize(entry.label));
            }

            final List<String> keywordList = new ArrayList<>();
            if (keywords != null) {
                for (final String value : keywords.split("[,\\n]")) {
                    final String keyword = normalize(value);
                    if (!keyword.isEmpty() && !keywordList.contains(keyword)) {
                        keywordList.add(keyword);
                    }
                }
            }
            return new Rules(enabled, videos, channels, channelNames, keywordList);
        }

        public boolean isBlocked(@Nullable final InfoItem item) {
            if (!enabled || item == null) {
                return false;
            }
            if (item instanceof StreamInfoItem) {
                final StreamInfoItem stream = (StreamInfoItem) item;
                return blockedVideoUrls.contains(normalize(stream.getUrl()))
                        || isBlockedChannel(stream.getUploaderUrl(), stream.getUploaderName())
                        || containsKeyword(stream.getName(), stream.getUploaderName());
            }
            if (item instanceof ChannelInfoItem) {
                return isBlockedChannel(item.getUrl(), item.getName())
                        || containsKeyword(item.getName());
            }
            if (item instanceof PlaylistInfoItem) {
                final PlaylistInfoItem playlist = (PlaylistInfoItem) item;
                return isBlockedChannel(null, playlist.getUploaderName())
                        || containsKeyword(playlist.getName(), playlist.getUploaderName());
            }
            if (item instanceof PostInfoItem) {
                final PostInfoItem post = (PostInfoItem) item;
                return isBlockedChannel(post.getUploaderUrl(), post.getUploaderName())
                        || containsKeyword(post.getName(), post.getContent(),
                                post.getUploaderName());
            }
            return containsKeyword(item.getName());
        }

        private boolean isBlockedChannel(@Nullable final String url,
                                         @Nullable final String name) {
            return blockedChannelKeys.contains(normalize(url))
                    || blockedChannelNames.contains(normalize(name));
        }

        private boolean containsKeyword(@Nullable final String... values) {
            for (final String value : values) {
                final String normalized = normalize(value);
                for (final String keyword : blockedKeywords) {
                    if (normalized.contains(keyword)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
