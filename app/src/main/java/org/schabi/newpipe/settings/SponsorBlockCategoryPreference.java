package org.schabi.newpipe.settings;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreferenceCompat;

import org.schabi.newpipe.R;
import org.schabi.newpipe.settings.sponsorblock.SponsorBlockCategoryConfig;

public class SponsorBlockCategoryPreference extends SwitchPreferenceCompat {
    public interface OnConfigureClickListener {
        void onConfigure(@NonNull SponsorBlockCategoryConfig category);
    }

    private SponsorBlockCategoryConfig category;
    private OnConfigureClickListener configureClickListener;

    public SponsorBlockCategoryPreference(@NonNull final Context context) {
        super(context);
        init();
    }

    public SponsorBlockCategoryPreference(@NonNull final Context context,
                                          @Nullable final AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setWidgetLayoutResource(R.layout.sponsor_block_category_widget);
        setIconSpaceReserved(false);
        setSingleLineTitle(false);
    }

    public void setCategory(@NonNull final SponsorBlockCategoryConfig category) {
        this.category = category;
        setKey(getContext().getString(category.enabledKeyResId));
        setTitle(category.titleResId);
        setSummary(category.summaryResId);
        setDefaultValue(category.defaultEnabled);
    }

    public void setOnConfigureClickListener(final OnConfigureClickListener newListener) {
        this.configureClickListener = newListener;
    }

    @Override
    public void onBindViewHolder(@NonNull final PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        final ImageButton button = (ImageButton) holder.findViewById(
                R.id.sponsor_block_category_settings);
        if (button == null) {
            return;
        }

        if (category == null || configureClickListener == null) {
            button.setVisibility(View.GONE);
            button.setOnClickListener(null);
            return;
        }

        button.setVisibility(isChecked() ? View.VISIBLE : View.GONE);
        button.setContentDescription(getContext().getString(
                R.string.sponsor_block_configure_category_content_description,
                getContext().getString(category.titleResId)));
        button.setFocusable(true);
        button.setOnClickListener(view -> configureClickListener.onConfigure(category));
    }
}
