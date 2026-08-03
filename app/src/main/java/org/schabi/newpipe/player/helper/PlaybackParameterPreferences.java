package org.schabi.newpipe.player.helper;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;

/**
 * Shared playback-parameter preference values used by the dialog and player gestures.
 */
public final class PlaybackParameterPreferences {
    public static final float STEP_1_PERCENT = 0.01f;
    public static final float STEP_5_PERCENT = 0.05f;
    public static final float STEP_10_PERCENT = 0.10f;
    public static final float STEP_25_PERCENT = 0.25f;
    public static final float STEP_100_PERCENT = 1.00f;
    public static final float DEFAULT_ADJUSTMENT_STEP = STEP_25_PERCENT;

    private static final float[] SUPPORTED_ADJUSTMENT_STEPS = {
            STEP_1_PERCENT,
            STEP_5_PERCENT,
            STEP_10_PERCENT,
            STEP_25_PERCENT,
            STEP_100_PERCENT
    };

    private PlaybackParameterPreferences() {
    }

    /**
     * Returns the selected adjustment step, falling back when stored data is unsupported.
     *
     * @param context context used to access the default shared preferences
     * @return a supported playback-parameter adjustment step
     */
    public static float getAdjustmentStep(@NonNull final Context context) {
        final float storedStep = PreferenceManager.getDefaultSharedPreferences(context)
                .getFloat(context.getString(R.string.adjustment_step_key),
                        DEFAULT_ADJUSTMENT_STEP);
        return sanitizeAdjustmentStep(storedStep);
    }

    /**
     * Restricts an adjustment step to the values exposed by the playback-parameter dialog.
     *
     * @param adjustmentStep candidate adjustment step
     * @return the candidate when supported, otherwise {@link #DEFAULT_ADJUSTMENT_STEP}
     */
    public static float sanitizeAdjustmentStep(final float adjustmentStep) {
        for (final float supportedStep : SUPPORTED_ADJUSTMENT_STEPS) {
            if (Float.compare(adjustmentStep, supportedStep) == 0) {
                return supportedStep;
            }
        }
        return DEFAULT_ADJUSTMENT_STEP;
    }
}
