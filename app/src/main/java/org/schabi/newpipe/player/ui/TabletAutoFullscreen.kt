package org.schabi.newpipe.player.ui

import android.content.Context
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import org.schabi.newpipe.util.DeviceUtils

/** Tablet opt-in and manual override, retained with the main player UI. */
class TabletAutoFullscreen {
    private var manualOrientation: Int? = null

    fun isAllowed(context: Context): Boolean = isAllowed(
        DeviceUtils.isTablet(context),
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(context.getString(R.string.tablet_auto_fullscreen_key), false),
        context.resources.configuration.orientation
    )

    internal fun isAllowed(tablet: Boolean, enabled: Boolean, orientation: Int): Boolean {
        if (manualOrientation != orientation) manualOrientation = null
        return !tablet || (enabled && manualOrientation == null)
    }

    fun onManualChange(orientation: Int) {
        manualOrientation = orientation
    }

    fun onNewVideo() {
        manualOrientation = null
    }
}
