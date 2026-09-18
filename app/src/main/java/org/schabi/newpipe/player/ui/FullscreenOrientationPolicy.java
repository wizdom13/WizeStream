package org.schabi.newpipe.player.ui;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;

import org.schabi.newpipe.player.helper.PlayerRotationMode;

/**
 * Central policy for main-player fullscreen, orientation and detail-layout transitions.
 *
 * <p>The player fullscreen flag and Android configuration can briefly disagree while a requested
 * rotation, fragment rebind or screen-off resume is being completed. Callers should express the
 * desired fullscreen state explicitly instead of deriving enter/exit intent from the current
 * orientation.</p>
 */
public final class FullscreenOrientationPolicy {
    public static final int KEEP_FULLSCREEN_STATE = -1;
    public static final int EXIT_FULLSCREEN = 0;
    public static final int ENTER_FULLSCREEN = 1;
    public static final int EXPANDED_DETAIL_MIN_WIDTH_DP = 840;

    private FullscreenOrientationPolicy() {
    }

    public static boolean shouldUseOrientationAction(final boolean verticalVideo,
                                                     final boolean landscape,
                                                     final boolean screenOrientationLocked) {
        return !verticalVideo || landscape && screenOrientationLocked;
    }

    public static int targetConfigurationOrientation(final boolean fullscreen,
                                                     final boolean verticalVideo) {
        return fullscreen && !verticalVideo
                ? Configuration.ORIENTATION_LANDSCAPE
                : Configuration.ORIENTATION_PORTRAIT;
    }

    public static boolean isTargetOrientation(final int currentOrientation,
                                              final int targetOrientation) {
        return currentOrientation == targetOrientation;
    }

    public static int requestedLandscapeOrientation(final PlayerRotationMode rotationMode) {
        return rotationMode == PlayerRotationMode.FIXED
                ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
    }

    public static int resolveFullscreenState(final int orientation,
                                             final boolean fullscreen,
                                             final boolean verticalVideo,
                                             final boolean phoneVideoEligible,
                                             final int pendingOrientation,
                                             final int pendingFullscreenState) {
        if (pendingFullscreenState != KEEP_FULLSCREEN_STATE) {
            return orientation == pendingOrientation
                    ? pendingFullscreenState : KEEP_FULLSCREEN_STATE;
        }
        if (!phoneVideoEligible) {
            return KEEP_FULLSCREEN_STATE;
        }
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return ENTER_FULLSCREEN;
        }
        if (orientation == Configuration.ORIENTATION_PORTRAIT
                && fullscreen && !verticalVideo) {
            return EXIT_FULLSCREEN;
        }
        return KEEP_FULLSCREEN_STATE;
    }

    public static boolean isFullscreenStateApplied(final int orientation,
                                                   final boolean fullscreen,
                                                   final boolean verticalVideo) {
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return fullscreen;
        }
        if (orientation == Configuration.ORIENTATION_PORTRAIT && !verticalVideo) {
            return !fullscreen;
        }
        return true;
    }

    public static boolean shouldKeepPhonePlayerLayoutForLandscape(
            final int orientation,
            final boolean playerAvailable,
            final boolean videoPlayerSelected,
            final boolean audioOnly,
            final boolean playerExpanded,
            final boolean largeScreenDevice) {
        return orientation == Configuration.ORIENTATION_LANDSCAPE
                && playerAvailable
                && videoPlayerSelected
                && !audioOnly
                && playerExpanded
                && !largeScreenDevice;
    }

    public static boolean shouldUseWideLandscapeDetailLayout(final int orientation,
                                                             final int screenWidthDp) {
        return orientation == Configuration.ORIENTATION_LANDSCAPE
                && screenWidthDp >= EXPANDED_DETAIL_MIN_WIDTH_DP;
    }

    public static boolean shouldRecreateDetailLayout(final boolean wideLandscapeLayout,
                                                     final int orientation,
                                                     final int screenWidthDp) {
        return wideLandscapeLayout
                != shouldUseWideLandscapeDetailLayout(orientation, screenWidthDp);
    }
}
