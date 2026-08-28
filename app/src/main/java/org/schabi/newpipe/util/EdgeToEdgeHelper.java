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
            final Insets safeInsets = getSafeSystemBarInsets(windowInsets);
            target.setPadding(
                    initialLeft + safeInsets.left,
                    initialTop + safeInsets.top,
                    initialRight + safeInsets.right,
                    initialBottom + safeInsets.bottom);

            return consumeAppliedInsets(windowInsets);
        });
        ViewCompat.requestApplyInsets(view);
    }

    /**
     * Applies insets to the independently laid-out content and drawer children of a
     * {@link androidx.drawerlayout.widget.DrawerLayout}. Padding the drawer layout itself does
     * not constrain those children, which can leave bottom navigation behind the system
     * navigation bar and drawer header content behind the status bar.
     *
     * <p>The drawer keeps drawing its background edge-to-edge. Only its header content receives
     * the top inset, while the drawer container receives horizontal cutout protection and the
     * bottom system-bar inset.</p>
     */
    public static void applyDrawerLayoutSystemBarPadding(
            @NonNull final View insetSource,
            @NonNull final View content,
            @NonNull final View drawer,
            @NonNull final View drawerHeader) {
        final int contentLeft = content.getPaddingLeft();
        final int contentTop = content.getPaddingTop();
        final int contentRight = content.getPaddingRight();
        final int contentBottom = content.getPaddingBottom();
        final int drawerLeft = drawer.getPaddingLeft();
        final int drawerTop = drawer.getPaddingTop();
        final int drawerRight = drawer.getPaddingRight();
        final int drawerBottom = drawer.getPaddingBottom();
        final int headerLeft = drawerHeader.getPaddingLeft();
        final int headerTop = drawerHeader.getPaddingTop();
        final int headerRight = drawerHeader.getPaddingRight();
        final int headerBottom = drawerHeader.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(insetSource, (target, windowInsets) -> {
            final Insets safeInsets = getSafeSystemBarInsets(windowInsets);
            content.setPadding(
                    contentLeft + safeInsets.left,
                    contentTop + safeInsets.top,
                    contentRight + safeInsets.right,
                    contentBottom + safeInsets.bottom);
            drawer.setPadding(
                    drawerLeft + safeInsets.left,
                    drawerTop,
                    drawerRight + safeInsets.right,
                    drawerBottom + safeInsets.bottom);
            drawerHeader.setPadding(
                    headerLeft,
                    headerTop + safeInsets.top,
                    headerRight,
                    headerBottom);

            return consumeAppliedInsets(windowInsets);
        });
        ViewCompat.requestApplyInsets(insetSource);
    }

    @NonNull
    private static Insets getSafeSystemBarInsets(
            @NonNull final WindowInsetsCompat windowInsets) {
        final Insets systemBars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars());
        final Insets displayCutout = windowInsets.getInsets(
                WindowInsetsCompat.Type.displayCutout());
        return Insets.max(systemBars, displayCutout);
    }

    @NonNull
    private static WindowInsetsCompat consumeAppliedInsets(
            @NonNull final WindowInsetsCompat windowInsets) {
        return new WindowInsetsCompat.Builder(windowInsets)
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.NONE)
                .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.NONE)
                .build();
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
