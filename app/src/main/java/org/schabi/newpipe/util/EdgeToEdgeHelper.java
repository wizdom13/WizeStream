package org.schabi.newpipe.util;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/** Shared edge-to-edge setup and system-bar inset handling for app activities. */
public final class EdgeToEdgeHelper {
    private EdgeToEdgeHelper() { }

    public static void enable(@NonNull final Activity activity) {
        final Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        makeSystemBarsTransparent(window);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }
        setLightSystemBars(activity, ThemeHelper.isLightThemeSelected(activity));
    }

    public static void applySystemBarPadding(@NonNull final View view) {
        final int initialLeft = view.getPaddingLeft();
        final int initialTop = view.getPaddingTop();
        final int initialRight = view.getPaddingRight();
        final int initialBottom = view.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(view, (target, windowInsets) -> {
            final Insets systemBars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars());
            final Insets displayCutout = windowInsets.getInsets(
                    WindowInsetsCompat.Type.displayCutout());
            final Insets safeInsets = Insets.max(systemBars, displayCutout);
            target.setPadding(
                    initialLeft + safeInsets.left,
                    initialTop + safeInsets.top,
                    initialRight + safeInsets.right,
                    initialBottom + safeInsets.bottom);

            return new WindowInsetsCompat.Builder(windowInsets)
                    .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.NONE)
                    .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.NONE)
                    .build();
        });
        ViewCompat.requestApplyInsets(view);
    }

    public static void showSystemBars(@NonNull final Activity activity) {
        final WindowInsetsControllerCompat controller = getInsetsController(activity);
        controller.show(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    public static void hideSystemBars(@NonNull final Activity activity,
                                      final boolean hideStatusBar) {
        final WindowInsetsControllerCompat controller = getInsetsController(activity);
        int types = WindowInsetsCompat.Type.navigationBars();
        if (hideStatusBar) {
            types |= WindowInsetsCompat.Type.statusBars();
        }
        controller.hide(types);
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    public static void setLightSystemBars(@NonNull final Activity activity,
                                          final boolean light) {
        final WindowInsetsControllerCompat controller = getInsetsController(activity);
        controller.setAppearanceLightStatusBars(light);
        controller.setAppearanceLightNavigationBars(light);
    }

    @NonNull
    private static WindowInsetsControllerCompat getInsetsController(
            @NonNull final Activity activity) {
        final Window window = activity.getWindow();
        return WindowCompat.getInsetsController(window, window.getDecorView());
    }

    @SuppressWarnings("deprecation")
    private static void makeSystemBarsTransparent(@NonNull final Window window) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        }
    }
}
