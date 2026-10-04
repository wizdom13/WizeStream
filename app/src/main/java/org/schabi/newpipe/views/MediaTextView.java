/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.views;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.MediaTextSize;

/** Opt-in title/channel/metadata sizing that also updates already visible and recycled items. */
public final class MediaTextView extends NewPipeTextView {
    private final SharedPreferences preferences;
    private final String preferenceKey;
    private final float defaultSizeSp;
    private float presentationScale = 1.0f;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;

    public MediaTextView(@NonNull final Context context) {
        this(context, null);
    }

    public MediaTextView(@NonNull final Context context, @Nullable final AttributeSet attrs) {
        this(context, attrs, android.R.attr.textViewStyle);
    }

    public MediaTextView(@NonNull final Context context, @Nullable final AttributeSet attrs,
                         final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        final TypedArray attributes = context.obtainStyledAttributes(
                attrs, R.styleable.MediaTextView, defStyleAttr, 0);
        final int role = attributes.getInt(R.styleable.MediaTextView_mediaTextRole, 0);
        attributes.recycle();
        preferenceKey = context.getString(switch (role) {
            case 1 -> R.string.channel_name_text_size_key;
            case 2 -> R.string.video_metadata_text_size_key;
            default -> R.string.video_title_text_size_key;
        });
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferenceListener = (prefs, key) -> {
            if (key == null || key.equals(preferenceKey)) {
                applyTextSize();
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ uses nonlinear accessibility font scaling.
            defaultSizeSp = TypedValue.deriveDimension(TypedValue.COMPLEX_UNIT_SP,
                    getTextSize(), getResources().getDisplayMetrics());
        } else {
            defaultSizeSp = getTextSize() / getResources().getDisplayMetrics().scaledDensity;
        }
        applyTextSize();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
        applyTextSize();
    }

    @Override
    protected void onDetachedFromWindow() {
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onConfigurationChanged(@NonNull final Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyTextSize();
    }

    /**
     * Applies a temporary presentation scale while retaining the user's chosen text size.
     *
     * @param scale additional scale used by the bottom player controls
     */
    public void setPresentationScale(final float scale) {
        presentationScale = scale;
        applyTextSize();
    }

    private void applyTextSize() {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, defaultSizeSp * presentationScale
                * MediaTextSize.scale(preferences.getString(preferenceKey, "100")));
    }
}
