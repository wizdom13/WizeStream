package org.schabi.newpipe.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.core.os.ConfigurationCompat;
import androidx.core.os.LocaleListCompat;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.App;
import org.schabi.newpipe.R;

import java.util.Locale;

public final class CaptionTranslationPreferences {
    private CaptionTranslationPreferences() {
    }

    @Nullable
    public static String getTargetLanguage() {
        try {
            return getTargetLanguage(App.getInstance());
        } catch (final RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    public static String getTargetLanguage(final Context context) {
        final SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        if (!preferences.getBoolean(
                context.getString(R.string.caption_auto_translate_key), false)) {
            return null;
        }

        final String systemValue = context.getString(R.string.caption_translation_system_value);
        final String configured = preferences.getString(
                context.getString(R.string.caption_translation_language_key), systemValue);
        if (configured == null || configured.trim().isEmpty()) {
            return null;
        }
        if (!systemValue.equals(configured)) {
            return configured.trim().replace('_', '-');
        }

        final LocaleListCompat locales = ConfigurationCompat.getLocales(
                context.getResources().getConfiguration());
        final Locale locale = locales.isEmpty() ? Locale.getDefault() : locales.get(0);
        if (locale == null) {
            return null;
        }
        final String languageTag = locale.toLanguageTag();
        return languageTag.isEmpty() ? locale.getLanguage() : languageTag;
    }

    public static void syncPreferredCaptionLanguage(final Context context) {
        final String targetLanguage = getTargetLanguage(context);
        if (targetLanguage == null) {
            return;
        }

        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(context.getString(R.string.caption_user_set_key), targetLanguage)
                .apply();
    }
}
