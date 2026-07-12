package org.schabi.newpipe.settings.sponsorblock;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockCategory;

public final class SponsorBlockCategoryRepository {
    public static final String MIGRATION_KEY = "sponsor_block_category_behavior_migration_v1";

    interface EnabledKeyResolver {
        @NonNull
        String getEnabledKey(@NonNull SponsorBlockCategoryConfig category);
    }

    private SponsorBlockCategoryRepository() {
    }

    public static SharedPreferences prefs(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static boolean isEnabled(@NonNull final Context context,
                                    @NonNull final SponsorBlockCategoryConfig category) {
        return isEnabled(prefs(context), category,
                item -> context.getString(item.enabledKeyResId));
    }

    public static void setEnabled(@NonNull final Context context,
                                  @NonNull final SponsorBlockCategoryConfig category,
                                  final boolean enabled) {
        prefs(context).edit()
                .putBoolean(context.getString(category.enabledKeyResId), enabled)
                .apply();
    }

    public static boolean isApiCategoryEnabled(@NonNull final Context context,
                                               final SponsorBlockCategory category) {
        final SponsorBlockCategoryConfig config =
                SponsorBlockCategoryConfig.fromApiCategory(category);
        return config != null && isEnabled(context, config);
    }

    @ColorInt
    public static int getColor(@NonNull final Context context,
                               @NonNull final SponsorBlockCategoryConfig category) {
        return getColor(prefs(context), category,
                ContextCompat.getColor(context, category.defaultColorResId));
    }

    @ColorInt
    public static int getColor(@NonNull final Context context,
                               final SponsorBlockCategory category) {
        final SponsorBlockCategoryConfig config =
                SponsorBlockCategoryConfig.fromApiCategory(category);
        return config == null
                ? ContextCompat.getColor(context, R.color.sponsor_block_filler)
                : getColor(context, config);
    }

    public static void setColor(@NonNull final Context context,
                                @NonNull final SponsorBlockCategoryConfig category,
                                @ColorInt final int color) {
        setColor(prefs(context), category, color);
    }

    public static void clearColorOverride(@NonNull final Context context,
                                          @NonNull final SponsorBlockCategoryConfig category) {
        clearColorOverride(prefs(context), category);
    }

    @NonNull
    public static SponsorBlockBehavior getBehavior(
            @NonNull final Context context,
            @NonNull final SponsorBlockCategoryConfig category) {
        return getBehavior(prefs(context), category);
    }

    @NonNull
    public static SponsorBlockBehavior getBehavior(@NonNull final Context context,
                                                   final SponsorBlockCategory category) {
        final SponsorBlockCategoryConfig config =
                SponsorBlockCategoryConfig.fromApiCategory(category);
        return config == null ? SponsorBlockBehavior.DONT_SKIP : getBehavior(context, config);
    }

    public static void setBehavior(@NonNull final Context context,
                                   @NonNull final SponsorBlockCategoryConfig category,
                                   @NonNull final SponsorBlockBehavior behavior) {
        setBehavior(prefs(context), category, behavior);
    }

    public static void setAllEnabled(@NonNull final Context context, final boolean enabled) {
        setAllEnabled(prefs(context), item -> context.getString(item.enabledKeyResId), enabled);
    }

    public static void resetDefaults(@NonNull final Context context) {
        resetDefaults(prefs(context), item -> context.getString(item.enabledKeyResId));
    }

    static boolean isEnabled(@NonNull final SharedPreferences preferences,
                             @NonNull final SponsorBlockCategoryConfig category,
                             @NonNull final EnabledKeyResolver enabledKeyResolver) {
        return preferences.getBoolean(enabledKeyResolver.getEnabledKey(category),
                category.defaultEnabled);
    }

    @ColorInt
    static int getColor(@NonNull final SharedPreferences preferences,
                        @NonNull final SponsorBlockCategoryConfig category,
                        @ColorInt final int defaultColor) {
        return (int) preferences.getLong(category.colorKey(), defaultColor);
    }

    static void setColor(@NonNull final SharedPreferences preferences,
                         @NonNull final SponsorBlockCategoryConfig category,
                         @ColorInt final int color) {
        preferences.edit().putLong(category.colorKey(), color | 0xFF000000L).apply();
    }

    static void clearColorOverride(@NonNull final SharedPreferences preferences,
                                   @NonNull final SponsorBlockCategoryConfig category) {
        preferences.edit().remove(category.colorKey()).apply();
    }

    @NonNull
    static SponsorBlockBehavior getBehavior(@NonNull final SharedPreferences preferences,
                                            @NonNull final SponsorBlockCategoryConfig category) {
        if (category.isMarkerOnly()) {
            return SponsorBlockBehavior.DONT_SKIP;
        }
        return SponsorBlockBehavior.fromValue(
                preferences.getString(category.behaviorKey(), category.defaultBehavior.value));
    }

    static void setBehavior(@NonNull final SharedPreferences preferences,
                            @NonNull final SponsorBlockCategoryConfig category,
                            @NonNull final SponsorBlockBehavior behavior) {
        final SharedPreferences.Editor editor = preferences.edit();
        if (category.isMarkerOnly()) {
            editor.remove(category.behaviorKey());
        } else {
            editor.putString(category.behaviorKey(), behavior.value);
        }
        editor.apply();
    }

    static void setAllEnabled(@NonNull final SharedPreferences preferences,
                              @NonNull final EnabledKeyResolver enabledKeyResolver,
                              final boolean enabled) {
        final SharedPreferences.Editor editor = preferences.edit();
        for (final SponsorBlockCategoryConfig category : SponsorBlockCategoryConfig.ALL) {
            editor.putBoolean(enabledKeyResolver.getEnabledKey(category), enabled);
        }
        editor.apply();
    }

    static void resetDefaults(@NonNull final SharedPreferences preferences,
                              @NonNull final EnabledKeyResolver enabledKeyResolver) {
        final SharedPreferences.Editor editor = preferences.edit();
        for (final SponsorBlockCategoryConfig category : SponsorBlockCategoryConfig.ALL) {
            editor.putBoolean(enabledKeyResolver.getEnabledKey(category), category.defaultEnabled);
            editor.remove(category.colorKey());
            if (category.isMarkerOnly()) {
                editor.remove(category.behaviorKey());
            } else {
                editor.putString(category.behaviorKey(), category.defaultBehavior.value);
            }
        }
        editor.apply();
    }

    /* One-shot migration from the removed global manual-skip affordance to per-category behavior.
     * The old option only delayed automatic skipping while showing a button. To preserve users'
     * effective automatic-skip behavior, migrated categories remain SKIP; new MANUAL is opt-in. */
    public static void migrateBehaviorOnce(@NonNull final Context context) {
        migrateBehaviorOnce(prefs(context));
    }

    static void migrateBehaviorOnce(@NonNull final SharedPreferences preferences) {
        if (preferences.getBoolean(MIGRATION_KEY, false)) {
            return;
        }

        final SharedPreferences.Editor editor = preferences.edit();
        for (final SponsorBlockCategoryConfig category : SponsorBlockCategoryConfig.ALL) {
            if (category.isMarkerOnly()) {
                editor.remove(category.behaviorKey());
            }
        }
        editor.putBoolean(MIGRATION_KEY, true).apply();
    }
}
