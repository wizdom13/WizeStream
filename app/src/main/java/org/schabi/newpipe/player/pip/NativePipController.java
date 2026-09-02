package org.schabi.newpipe.player.pip;

import android.app.PictureInPictureParams;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Build;
import android.util.Rational;

import androidx.annotation.ChecksSdkIntAtLeast;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.fragments.detail.VideoDetailFragment;
import org.schabi.newpipe.util.DeviceUtils;

import java.util.Optional;

/** Coordinates Android's native picture-in-picture lifecycle with the main player fragment. */
public final class NativePipController {
    private static final float MIN_ASPECT_RATIO = 1.0f / 2.39f;
    private static final float MAX_ASPECT_RATIO = 2.39f;

    @NonNull
    private final AppCompatActivity activity;

    public NativePipController(@NonNull final AppCompatActivity activity) {
        this.activity = activity;
    }

    public void updatePictureInPictureParams() {
        if (!isSupported()) {
            return;
        }
        try {
            activity.setPictureInPictureParams(buildParams(currentFragment().orElse(null)));
        } catch (final IllegalStateException ignored) {
            // Some vendor builds reject PiP calls even after advertising support.
        }
    }

    public void onUserLeaveHint() {
        if (!isSupported() || !isEnabled()) {
            return;
        }
        final VideoDetailFragment fragment = currentFragment().orElse(null);
        if (fragment == null || !fragment.isNativePipEligible()) {
            return;
        }

        fragment.prepareNativePipEntry();
        final PictureInPictureParams params = buildParams(fragment);
        try {
            activity.setPictureInPictureParams(params);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                    || !fragment.isNativePipPlaying()) {
                if (!activity.enterPictureInPictureMode(params)) {
                    fragment.onNativePipModeChanged(false);
                }
            }
        } catch (final IllegalStateException ignored) {
            fragment.onNativePipModeChanged(false);
        }
    }

    /**
     * Enters native PiP immediately from an explicit user action.
     *
     * <p>Unlike the Home-button path, this deliberately avoids changing the fullscreen/detail
     * layout before asking Android to start the PiP transition. The normal PiP mode callback will
     * perform the existing player preparation after the system has started the transition.</p>
     *
     * @return whether Android accepted the PiP entry request
     */
    public boolean enterPictureInPicture() {
        if (!isSupported()) {
            return false;
        }
        final VideoDetailFragment fragment = currentFragment().orElse(null);
        if (fragment == null || !fragment.isNativePipEligible()) {
            return false;
        }

        final PictureInPictureParams params = buildParams(fragment);
        try {
            activity.setPictureInPictureParams(params);
            return activity.enterPictureInPictureMode(params);
        } catch (final IllegalArgumentException | IllegalStateException ignored) {
            return false;
        }
    }

    public void onPictureInPictureModeChanged(final boolean inPictureInPictureMode) {
        currentFragment().ifPresent(fragment ->
                fragment.onNativePipModeChanged(inPictureInPictureMode));
        if (!inPictureInPictureMode) {
            updatePictureInPictureParams();
        }
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    private boolean isSupported() {
        // Keep this guard before evaluating any newer Activity APIs. Java evaluates method
        // arguments eagerly, so passing isInMultiWindowMode() into the helper on API 23 would
        // still invoke that API even though the helper itself rejects pre-Oreo versions.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false;
        }
        return isSupportedEnvironment(
                Build.VERSION.SDK_INT,
                activity.getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_PICTURE_IN_PICTURE),
                DeviceUtils.isTv(activity),
                activity.isInMultiWindowMode());
    }

    static boolean isSupportedEnvironment(final int sdkInt,
                                          final boolean hasSystemFeature,
                                          final boolean isTv,
                                          final boolean isInMultiWindowMode) {
        return sdkInt >= Build.VERSION_CODES.O && hasSystemFeature
                && !isTv && !isInMultiWindowMode;
    }

    private boolean isEnabled() {
        return PreferenceManager.getDefaultSharedPreferences(activity).getBoolean(
                activity.getString(R.string.native_pip_key), false);
    }

    @NonNull
    private Optional<VideoDetailFragment> currentFragment() {
        final Fragment fragment = activity.getSupportFragmentManager()
                .findFragmentById(R.id.fragment_player_holder);
        return fragment instanceof VideoDetailFragment
                ? Optional.of((VideoDetailFragment) fragment) : Optional.empty();
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @NonNull
    private PictureInPictureParams buildParams(final VideoDetailFragment fragment) {
        final PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();
        if (fragment != null) {
            final float aspectRatio = sanitizeAspectRatio(fragment.getNativePipAspectRatio());
            builder.setAspectRatio(new Rational(Math.round(aspectRatio * 10_000), 10_000));
            final Rect sourceRect = fragment.getNativePipSourceRect();
            if (!sourceRect.isEmpty()) {
                builder.setSourceRectHint(sourceRect);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            final boolean autoEnter = isEnabled() && fragment != null
                    && fragment.isNativePipEligible() && fragment.isNativePipPlaying();
            builder.setAutoEnterEnabled(autoEnter);
            builder.setSeamlessResizeEnabled(true);
        }
        return builder.build();
    }

    static float sanitizeAspectRatio(final float aspectRatio) {
        if (!Float.isFinite(aspectRatio) || aspectRatio <= 0.0f) {
            return 16.0f / 9.0f;
        }
        return Math.max(MIN_ASPECT_RATIO, Math.min(aspectRatio, MAX_ASPECT_RATIO));
    }
}
