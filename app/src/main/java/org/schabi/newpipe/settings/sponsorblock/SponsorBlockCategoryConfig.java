package org.schabi.newpipe.settings.sponsorblock;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockCategory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("checkstyle:ParameterNumber")
public enum SponsorBlockCategoryConfig {
    SPONSOR("sponsor", SponsorBlockCategory.SPONSOR,
            R.string.sponsor_block_category_sponsor_key,
            R.string.sponsor_block_category_sponsor_title,
            R.string.sponsor_block_category_sponsor_summary, true,
            R.color.sponsor_block_sponsor, SponsorBlockBehavior.SKIP),
    INTRO("intro", SponsorBlockCategory.INTRO,
            R.string.sponsor_block_category_intro_key,
            R.string.sponsor_block_category_intro_title,
            R.string.sponsor_block_category_intro_summary, false,
            R.color.sponsor_block_intro, SponsorBlockBehavior.SKIP),
    OUTRO("outro", SponsorBlockCategory.OUTRO,
            R.string.sponsor_block_category_outro_key,
            R.string.sponsor_block_category_outro_title,
            R.string.sponsor_block_category_outro_summary, false,
            R.color.sponsor_block_outro, SponsorBlockBehavior.SKIP),
    INTERACTION("interaction", SponsorBlockCategory.INTERACTION,
            R.string.sponsor_block_category_interaction_key,
            R.string.sponsor_block_category_interaction_title,
            R.string.sponsor_block_category_interaction_summary, false,
            R.color.sponsor_block_interaction, SponsorBlockBehavior.SKIP),
    SELF_PROMO("self_promo", SponsorBlockCategory.SELF_PROMO,
            R.string.sponsor_block_category_self_promo_key,
            R.string.sponsor_block_category_self_promo_title,
            R.string.sponsor_block_category_self_promo_summary, false,
            R.color.sponsor_block_self_promo, SponsorBlockBehavior.SKIP),
    NON_MUSIC("non_music", SponsorBlockCategory.NON_MUSIC,
            R.string.sponsor_block_category_non_music_key,
            R.string.sponsor_block_category_non_music_title,
            R.string.sponsor_block_category_non_music_summary, false,
            R.color.sponsor_block_music, SponsorBlockBehavior.SKIP),
    PREVIEW("preview", SponsorBlockCategory.PREVIEW,
            R.string.sponsor_block_category_preview_key,
            R.string.sponsor_block_category_preview_title,
            R.string.sponsor_block_category_preview_summary, false,
            R.color.sponsor_block_preview, SponsorBlockBehavior.SKIP),
    FILLER("filler", SponsorBlockCategory.FILLER,
            R.string.sponsor_block_category_filler_key,
            R.string.sponsor_block_category_filler_title,
            R.string.sponsor_block_category_filler_summary, false,
            R.color.sponsor_block_filler, SponsorBlockBehavior.SKIP),
    HIGHLIGHT("highlight", SponsorBlockCategory.HIGHLIGHT,
            R.string.sponsor_block_category_highlight_key,
            R.string.sponsor_block_category_highlight_title,
            R.string.sponsor_block_category_highlight_summary, false,
            R.color.sponsor_block_highlight, SponsorBlockBehavior.DONT_SKIP);

    public static final List<SponsorBlockCategoryConfig> ALL =
            Collections.unmodifiableList(Arrays.asList(values()));

    @NonNull
    public final String id;
    @NonNull
    public final SponsorBlockCategory apiCategory;
    @StringRes
    public final int enabledKeyResId;
    @StringRes
    public final int titleResId;
    @StringRes
    public final int summaryResId;
    public final boolean defaultEnabled;
    @ColorRes
    public final int defaultColorResId;
    @NonNull
    public final SponsorBlockBehavior defaultBehavior;

    SponsorBlockCategoryConfig(@NonNull final String id,
                               @NonNull final SponsorBlockCategory apiCategory,
                               @StringRes final int enabledKeyResId,
                               @StringRes final int titleResId,
                               @StringRes final int summaryResId,
                               final boolean defaultEnabled,
                               @ColorRes final int defaultColorResId,
                               @NonNull final SponsorBlockBehavior defaultBehavior) {
        this.id = id;
        this.apiCategory = apiCategory;
        this.enabledKeyResId = enabledKeyResId;
        this.titleResId = titleResId;
        this.summaryResId = summaryResId;
        this.defaultEnabled = defaultEnabled;
        this.defaultColorResId = defaultColorResId;
        this.defaultBehavior = defaultBehavior;
    }

    public String colorKey() {
        return "sponsor_block_category_" + id + "_color";
    }

    public String behaviorKey() {
        return "sponsor_block_category_" + id + "_behavior";
    }

    public boolean isMarkerOnly() {
        return this == HIGHLIGHT;
    }

    public static SponsorBlockCategoryConfig fromId(final String id) {
        for (final SponsorBlockCategoryConfig category : values()) {
            if (category.id.equals(id)) {
                return category;
            }
        }
        return null;
    }

    public static SponsorBlockCategoryConfig fromApiCategory(
            final SponsorBlockCategory category) {
        for (final SponsorBlockCategoryConfig config : values()) {
            if (config.apiCategory == category) {
                return config;
            }
        }
        return null;
    }
}
