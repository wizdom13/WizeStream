package org.schabi.newpipe.settings.preferencesearch;

import android.content.Context;
import android.os.Build;

import androidx.core.os.ConfigurationCompat;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.settings.SettingsResourceRegistry;
import org.schabi.newpipe.settings.sponsorblock.SponsorBlockCategoryConfig;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.ReleaseVersionUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Builds an on-device index without creating settings fragments or changing preferences. */
public final class SettingsSearchIndex {
    private SettingsSearchIndex() {
    }

    public static PreferenceSearcher build(final Context context) {
        final PreferenceSearchConfiguration configuration = new PreferenceSearchConfiguration();
        final PreferenceParser parser = new PreferenceParser(context, configuration);
        final PreferenceSearcher searcher = new PreferenceSearcher(configuration);
        for (final SettingsResourceRegistry.SettingRegistryEntry entry
                : SettingsResourceRegistry.getInstance().getAllEntries()) {
            final int xml = entry.getPreferencesResId();
            final boolean debug = xml == R.xml.debug_settings && MainActivity.DEBUG;
            if ((!entry.isSearchable() && !debug)
                    || (xml == R.xml.update_settings
                        && !ReleaseVersionUtil.INSTANCE.isReleaseApk())) {
                continue;
            }
            searcher.add(parser.parse(xml).stream().filter(item -> isAvailable(context, item))
                    .map(item -> withDeviceSummary(context, item))
                    .collect(Collectors.toList()));
        }

        // These preferences are created in code, so they have no corresponding XML rows.
        final List<PreferenceSearchItem> dynamic = new ArrayList<>();
        final String captions = context.getString(R.string.settings_category_video_audio_title)
                + " > " + context.getString(R.string.caption_translation_category_title);
        dynamic.add(new PreferenceSearchItem(context.getString(R.string.caption_auto_translate_key),
                context.getString(R.string.caption_auto_translate_title),
                context.getString(R.string.caption_auto_translate_summary), "", captions,
                R.xml.video_audio_settings));
        Locale displayLocale = ConfigurationCompat.getLocales(context.getResources()
                .getConfiguration()).get(0);
        if (displayLocale == null) {
            displayLocale = Locale.getDefault();
        }
        final StringBuilder languages = new StringBuilder(
                context.getString(R.string.caption_translation_system_language));
        for (final String language : Locale.getISOLanguages()) {
            languages.append(", ").append(Locale.forLanguageTag(language)
                    .getDisplayLanguage(displayLocale));
        }
        dynamic.add(new PreferenceSearchItem(
                context.getString(R.string.caption_translation_language_key),
                context.getString(R.string.caption_translation_language_title), "",
                languages.toString(), captions, R.xml.video_audio_settings));
        final String categories = context.getString(R.string.sponsor_block_settings_title)
                + " > " + context.getString(R.string.sponsor_block_categories_title);
        for (final SponsorBlockCategoryConfig category : SponsorBlockCategoryConfig.ALL) {
            dynamic.add(new PreferenceSearchItem(context.getString(category.enabledKeyResId),
                    context.getString(category.titleResId),
                    context.getString(category.summaryResId),
                    "", categories, R.xml.sponsor_block_categories_settings));
        }
        searcher.add(dynamic);
        return searcher;
    }

    private static PreferenceSearchItem withDeviceSummary(final Context context,
                                                          final PreferenceSearchItem item) {
        if ("notification_actions".equals(item.getKey())
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new PreferenceSearchItem(item.getKey(), item.getTitle(),
                    context.getString(R.string.notification_actions_summary_android13),
                    item.getEntries(), item.getBreadcrumbs(), item.getSearchIndexItemResId());
        }
        return item;
    }

    private static boolean isAvailable(final Context context, final PreferenceSearchItem item) {
        final String key = item.getKey();
        if (key.equals(context.getString(R.string.app_language_key))) {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU;
        }
        if (key.equals(context.getString(R.string.app_language_android_13_and_up_key))) {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
        }
        if (key.equals(context.getString(R.string.native_pip_key))
                || key.equals(context.getString(R.string.primary_floating_player_action_key))) {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
        }
        if (key.equals(context.getString(R.string.grid_columns_key))
                || key.equals(context.getString(R.string.tablet_navigation_portrait_position_key))
                || key.equals(context.getString(
                        R.string.tablet_navigation_landscape_position_key))) {
            return DeviceUtils.isTablet(context);
        }
        return true;
    }
}
