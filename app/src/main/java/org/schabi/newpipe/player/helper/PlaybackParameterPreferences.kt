package org.schabi.newpipe.player.helper

import android.content.Context
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R

/** Shared playback-parameter preference values used by the dialog and player gestures. */
object PlaybackParameterPreferences {
    const val STEP_1_PERCENT = 0.01f
    const val STEP_5_PERCENT = 0.05f
    const val STEP_10_PERCENT = 0.10f
    const val STEP_25_PERCENT = 0.25f
    const val STEP_100_PERCENT = 1.00f
    const val DEFAULT_ADJUSTMENT_STEP = STEP_25_PERCENT

    private val supportedAdjustmentSteps = floatArrayOf(
        STEP_1_PERCENT,
        STEP_5_PERCENT,
        STEP_10_PERCENT,
        STEP_25_PERCENT,
        STEP_100_PERCENT
    )

    /** Returns the selected adjustment step, falling back when stored data is unsupported. */
    @JvmStatic
    fun getAdjustmentStep(context: Context): Float {
        val storedStep = PreferenceManager.getDefaultSharedPreferences(context).getFloat(
            context.getString(R.string.adjustment_step_key),
            DEFAULT_ADJUSTMENT_STEP
        )
        return sanitizeAdjustmentStep(storedStep)
    }

    /** Restricts an adjustment step to the values exposed by the playback-parameter dialog. */
    @JvmStatic
    fun sanitizeAdjustmentStep(adjustmentStep: Float): Float = supportedAdjustmentSteps.firstOrNull {
        java.lang.Float.compare(adjustmentStep, it) == 0
    } ?: DEFAULT_ADJUSTMENT_STEP
}
