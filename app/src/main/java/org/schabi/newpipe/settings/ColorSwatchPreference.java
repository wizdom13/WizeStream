package org.schabi.newpipe.settings;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import org.schabi.newpipe.R;

public class ColorSwatchPreference extends Preference {
    @ColorInt
    private int color;

    public ColorSwatchPreference(@NonNull final Context context) {
        super(context);
        init();
    }

    public ColorSwatchPreference(@NonNull final Context context,
                                 @Nullable final AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setWidgetLayoutResource(R.layout.sponsor_block_color_swatch_widget);
        setIconSpaceReserved(false);
        setSingleLineTitle(false);
    }

    public void setColor(@ColorInt final int color) {
        this.color = color;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull final PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        final View swatch = holder.findViewById(R.id.sponsor_block_color_swatch);
        if (swatch != null) {
            final GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(color);
            swatch.setBackground(drawable);
        }
    }
}
