package org.schabi.newpipe.player.gesture;

import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED;
import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED;
import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.OrientationEventListener;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.helper.PlayerHolder;
import org.schabi.newpipe.util.DeviceUtils;

/**
 * Allows the main video player to follow the physical phone orientation even while Android's
 * system rotation is locked. The controller only claims fullscreen transitions that it initiated,
 * so manually-entered fullscreen remains entirely under the user's control.
 */
final class LockedOrientationFullscreenController {
    static final int ORIENTATION_ZONE_OTHER = 0;
    static final int ORIENTATION_ZONE_PORTRAIT = 1;
    static final int ORIENTATION_ZONE_LANDSCAPE = 2;

    private static final int ORIENTATION_TOLERANCE_DEGREES = 30;
    private static final long STABLE_ORIENTATION_DELAY_MILLIS = 250L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int sheetState = STATE_HIDDEN;
    private int pendingOrientationZone = ORIENTATION_ZONE_OTHER;
    private int stableOrientationZone = ORIENTATION_ZONE_OTHER;
    private boolean autoEnteredFullscreen;
    private boolean listening;

    @Nullable
    private View bottomSheet;
    @Nullable
    private Activity activity;
    @Nullable
    private LifecycleOwner lifecycleOwner;
    @Nullable
    private OrientationEventListener orientationEventListener;

    private final Runnable commitPendingOrientation = () -> {
        if (pendingOrientationZone == ORIENTATION_ZONE_OTHER) {
            return;
        }
        final int committedZone = pendingOrientationZone;
        pendingOrientationZone = ORIENTATION_ZONE_OTHER;
        if (handleStableOrientation(committedZone)) {
            stableOrientationZone = committedZone;
        }
    };

    private final LifecycleEventObserver lifecycleObserver = (source, event) -> {
        if (event == Lifecycle.Event.ON_START) {
            startListening();
        } else if (event == Lifecycle.Event.ON_STOP) {
            stopListening();
        } else if (event == Lifecycle.Event.ON_DESTROY) {
            detach();
        }
    };

    private final View.OnAttachStateChangeListener attachStateChangeListener =
            new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(final View view) {
                    if (lifecycleOwner == null) {
                        startListening();
                    }
                }

                @Override
                public void onViewDetachedFromWindow(final View view) {
                    if (view == bottomSheet) {
                        detach();
                    }
                }
            };

    void attach(@NonNull final View playerSheet, final int currentSheetState) {
        sheetState = currentSheetState;
        if (bottomSheet == playerSheet) {
            return;
        }

        detach();
        bottomSheet = playerSheet;
        sheetState = currentSheetState;
        activity = findActivity(playerSheet.getContext());
        if (activity == null) {
            return;
        }

        orientationEventListener = new OrientationEventListener(activity) {
            @Override
            public void onOrientationChanged(final int orientation) {
                handleOrientationChanged(orientation);
            }
        };
        playerSheet.addOnAttachStateChangeListener(attachStateChangeListener);

        if (activity instanceof LifecycleOwner owner) {
            lifecycleOwner = owner;
            owner.getLifecycle().addObserver(lifecycleObserver);
            if (owner.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                startListening();
            }
        } else if (playerSheet.isAttachedToWindow()) {
            startListening();
        }
    }

    void onPlayerSheetStateChanged(final int newState) {
        sheetState = newState;
        if (newState == STATE_EXPANDED) {
            cancelPendingOrientation();
            stableOrientationZone = ORIENTATION_ZONE_OTHER;
        } else if (newState == STATE_COLLAPSED || newState == STATE_HIDDEN) {
            cancelPendingOrientation();
            stableOrientationZone = ORIENTATION_ZONE_OTHER;
            // The normal collapsed-player path restores orientation/fullscreen. Do not let a
            // previous automatic entry claim a later manual fullscreen session.
            autoEnteredFullscreen = false;
        }
    }

    private void detach() {
        stopListening();
        if (bottomSheet != null) {
            bottomSheet.removeOnAttachStateChangeListener(attachStateChangeListener);
        }
        if (lifecycleOwner != null) {
            lifecycleOwner.getLifecycle().removeObserver(lifecycleObserver);
        }
        bottomSheet = null;
        activity = null;
        lifecycleOwner = null;
        orientationEventListener = null;
        sheetState = STATE_HIDDEN;
        autoEnteredFullscreen = false;
    }

    private void startListening() {
        if (listening || activity == null || orientationEventListener == null
                || !isFeatureEnabled(activity)
                || isLargeScreenDevice(activity)
                || !orientationEventListener.canDetectOrientation()) {
            return;
        }
        orientationEventListener.enable();
        listening = true;
    }

    private void stopListening() {
        if (orientationEventListener != null && listening) {
            orientationEventListener.disable();
        }
        listening = false;
        cancelPendingOrientation();
        stableOrientationZone = ORIENTATION_ZONE_OTHER;
    }

    private void handleOrientationChanged(final int orientation) {
        if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
            cancelPendingOrientation();
            return;
        }

        final int orientationZone = orientationZone(orientation);
        if (orientationZone == ORIENTATION_ZONE_OTHER) {
            cancelPendingOrientation();
            return;
        }
        if (orientationZone == ORIENTATION_ZONE_LANDSCAPE && sheetState != STATE_EXPANDED) {
            cancelPendingOrientation();
            return;
        }
        if (orientationZone == stableOrientationZone
                || orientationZone == pendingOrientationZone) {
            return;
        }

        handler.removeCallbacks(commitPendingOrientation);
        pendingOrientationZone = orientationZone;
        handler.postDelayed(commitPendingOrientation, STABLE_ORIENTATION_DELAY_MILLIS);
    }

    private void cancelPendingOrientation() {
        handler.removeCallbacks(commitPendingOrientation);
        pendingOrientationZone = ORIENTATION_ZONE_OTHER;
    }

    private boolean handleStableOrientation(final int orientationZone) {
        final Activity currentActivity = activity;
        if (currentActivity == null) {
            return false;
        }

        if (orientationZone == ORIENTATION_ZONE_LANDSCAPE) {
            final PlayerHolder playerHolder = PlayerHolder.getInstance();
            final boolean featureEnabled = isFeatureEnabled(currentActivity);
            final boolean systemRotationLocked =
                    PlayerHelper.globalScreenOrientationLocked(currentActivity);
            final boolean playerExpanded = sheetState == STATE_EXPANDED;
            final boolean videoPlayerEligible =
                    playerHolder.isMainVideoPlayerOrientationEligible();
            final boolean alreadyFullscreen = playerHolder.isMainPlayerFullscreen();
            final boolean explicitLandscapeRequest = isExplicitLandscapeOrientation(
                    currentActivity.getRequestedOrientation());
            final boolean inPictureInPicture = isInPictureInPicture(currentActivity);
            final boolean largeScreenDevice = isLargeScreenDevice(currentActivity);

            if (shouldAutoEnterFullscreen(
                    featureEnabled,
                    systemRotationLocked,
                    playerExpanded,
                    videoPlayerEligible,
                    alreadyFullscreen,
                    explicitLandscapeRequest,
                    inPictureInPicture,
                    largeScreenDevice)) {
                autoEnteredFullscreen = true;
                currentActivity.setRequestedOrientation(
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                return true;
            }

            // Keep checking while the expanded player is still becoming eligible or PiP is
            // finishing. Permanent/manual blocks can be treated as a stable landscape state.
            return playerExpanded && videoPlayerEligible && !inPictureInPicture;
        }

        if (shouldAutoExitFullscreen(autoEnteredFullscreen, orientationZone)) {
            autoEnteredFullscreen = false;
            currentActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
        return true;
    }

    private static boolean isFeatureEnabled(@NonNull final Activity activity) {
        return PreferenceManager.getDefaultSharedPreferences(activity).getBoolean(
                activity.getString(R.string.rotate_to_fullscreen_key), true);
    }

    private static boolean isInPictureInPicture(@NonNull final Activity activity) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && activity.isInPictureInPictureMode();
    }

    private static boolean isLargeScreenDevice(@NonNull final Activity activity) {
        return DeviceUtils.isTablet(activity)
                || DeviceUtils.isTv(activity)
                || DeviceUtils.isDesktopMode(activity);
    }

    static int orientationZone(final int orientation) {
        if (orientation < 0) {
            return ORIENTATION_ZONE_OTHER;
        }
        final int normalized = orientation % 360;
        if (normalized <= ORIENTATION_TOLERANCE_DEGREES
                || normalized >= 360 - ORIENTATION_TOLERANCE_DEGREES) {
            return ORIENTATION_ZONE_PORTRAIT;
        }
        if (Math.abs(normalized - 90) <= ORIENTATION_TOLERANCE_DEGREES
                || Math.abs(normalized - 270) <= ORIENTATION_TOLERANCE_DEGREES) {
            return ORIENTATION_ZONE_LANDSCAPE;
        }
        return ORIENTATION_ZONE_OTHER;
    }

    static boolean shouldAutoEnterFullscreen(final boolean featureEnabled,
                                             final boolean systemRotationLocked,
                                             final boolean playerExpanded,
                                             final boolean videoPlayerEligible,
                                             final boolean alreadyFullscreen,
                                             final boolean explicitLandscapeRequest,
                                             final boolean inPictureInPicture,
                                             final boolean largeScreenDevice) {
        return featureEnabled
                && systemRotationLocked
                && playerExpanded
                && videoPlayerEligible
                && !alreadyFullscreen
                && !explicitLandscapeRequest
                && !inPictureInPicture
                && !largeScreenDevice;
    }

    static boolean shouldAutoExitFullscreen(final boolean autoEnteredFullscreen,
                                            final int orientationZone) {
        return autoEnteredFullscreen && orientationZone == ORIENTATION_ZONE_PORTRAIT;
    }

    static boolean isExplicitLandscapeOrientation(final int requestedOrientation) {
        return requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                || requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                || requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                || requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE;
    }

    @Nullable
    private static Activity findActivity(@NonNull final Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                return (Activity) current;
            }
            final Context base = ((ContextWrapper) current).getBaseContext();
            if (base == current) {
                break;
            }
            current = base;
        }
        return current instanceof Activity ? (Activity) current : null;
    }
}
