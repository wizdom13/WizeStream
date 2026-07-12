package org.schabi.newpipe.settings.sponsorblock;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.schabi.newpipe.R;

public enum SponsorBlockBehavior {
    SKIP("skip", R.string.sponsor_block_behavior_skip_title),
    MANUAL("manual", R.string.sponsor_block_behavior_manual_title),
    DONT_SKIP("dont_skip", R.string.sponsor_block_behavior_dont_skip_title);

    @NonNull
    public final String value;
    @StringRes
    public final int titleResId;

    SponsorBlockBehavior(@NonNull final String value, @StringRes final int titleResId) {
        this.value = value;
        this.titleResId = titleResId;
    }

    @NonNull
    public static SponsorBlockBehavior fromValue(final String value) {
        for (final SponsorBlockBehavior behavior : values()) {
            if (behavior.value.equals(value)) {
                return behavior;
            }
        }
        return SKIP;
    }
}
