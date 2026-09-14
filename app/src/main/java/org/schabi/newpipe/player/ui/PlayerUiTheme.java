package org.schabi.newpipe.player.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.google.android.material.color.DynamicColors;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.ThemeHelper;

/** Owns the seek-bar palette for a video UI, including service-hosted popup playback. */
public final class PlayerUiTheme implements AutoCloseable {
    private final Context context;
    private final SeekBar seekBar;
    private final SharedPreferences preferences;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences.OnSharedPreferenceChangeListener listener =
            this::onThemeChanged;
    private boolean closed;

    public PlayerUiTheme(@NonNull final Context context, @NonNull final SeekBar seekBar) {
        this.context = context;
        this.seekBar = seekBar;
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferences.registerOnSharedPreferenceChangeListener(listener);
        refresh();
    }

    /**
     * Build a fresh player palette without changing the service's theme or retaining an activity.
     * @param context the player service context
     * @return a context suitable for inflating player controls
     */
    @NonNull
    public static Context createContext(@NonNull final Context context) {
        final boolean light = ThemeHelper.isLightThemeSelected(context);
        final Configuration configuration = new Configuration(context.getResources()
                .getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (light ? Configuration.UI_MODE_NIGHT_NO : Configuration.UI_MODE_NIGHT_YES);
        Context themed = new ContextThemeWrapper(context.createConfigurationContext(configuration),
                ThemeHelper.getThemeForService(context, -1));
        if (ThemeHelper.isFollowSystemThemeColor(context)) {
            final int overlay = light
                    ? com.google.android.material.R.style.ThemeOverlay_Material3_DynamicColors_Light
                    : com.google.android.material.R.style.ThemeOverlay_Material3_DynamicColors_Dark;
            themed = DynamicColors.wrapContextIfAvailable(themed, overlay);
            if (ThemeHelper.isBlackThemeSelected(context)) {
                themed.getTheme().applyStyle(R.style.ThemeOverlay_wizestream_BlackSurfaces, true);
            }
        } else {
            ThemeHelper.applyThemeColorOverlay(themed);
        }
        return themed;
    }

    public void refresh() {
        if (!closed) {
            // A service and its existing views can outlive both the activity and its palette.
            applyColors(seekBar, createContext(context));
        }
    }

    static void applyColors(final SeekBar seekBar, final Context palette) {
        final ColorStateList active = color(palette, R.attr.colorPrimary);
        seekBar.setProgressTintList(active);
        seekBar.setProgressTintMode(PorterDuff.Mode.SRC_IN);
        seekBar.setThumbTintList(active);
        seekBar.setThumbTintMode(PorterDuff.Mode.SRC_IN);
        seekBar.setSecondaryProgressTintList(color(palette,
                com.google.android.material.R.attr.colorPrimaryContainer));
        seekBar.setSecondaryProgressTintMode(PorterDuff.Mode.SRC_IN);
        seekBar.setProgressBackgroundTintList(color(palette,
                com.google.android.material.R.attr.colorSurfaceVariant));
        seekBar.setProgressBackgroundTintMode(PorterDuff.Mode.SRC_IN);
    }

    private static ColorStateList color(final Context palette, final int attribute) {
        return ColorStateList.valueOf(ThemeHelper.resolveColorFromAttr(palette, attribute));
    }

    private void onThemeChanged(final SharedPreferences unused, final String key) {
        if (key == null || key.equals(context.getString(R.string.theme_key))
                || key.equals(context.getString(R.string.night_theme_key))
                || key.equals(context.getString(R.string.theme_color_key))) {
            handler.removeCallbacksAndMessages(null);
            handler.post(this::refresh);
        }
    }

    @Override
    public void close() {
        closed = true;
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
        handler.removeCallbacksAndMessages(null);
    }
}
