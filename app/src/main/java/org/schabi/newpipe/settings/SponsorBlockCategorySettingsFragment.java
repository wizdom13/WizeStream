package org.schabi.newpipe.settings;

import androidx.appcompat.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.schabi.newpipe.R;
import org.schabi.newpipe.settings.sponsorblock.SponsorBlockBehavior;
import org.schabi.newpipe.settings.sponsorblock.SponsorBlockCategoryConfig;
import org.schabi.newpipe.settings.sponsorblock.SponsorBlockCategoryRepository;
import org.schabi.newpipe.util.ServiceHelper;

import java.util.Locale;
import java.util.regex.Pattern;

public class SponsorBlockCategorySettingsFragment extends BasePreferenceFragment {
    public static final String ARG_CATEGORY_ID = "category_id";
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("#?[0-9a-fA-F]{6}");

    private SponsorBlockCategoryConfig category;
    private ColorSwatchPreference colorPreference;
    private Preference behaviorPreference;
    private boolean updatingColorControls;

    public static SponsorBlockCategorySettingsFragment newInstance(
            @NonNull final String categoryId) {
        final SponsorBlockCategorySettingsFragment fragment =
                new SponsorBlockCategorySettingsFragment();
        final Bundle arguments = new Bundle();
        arguments.putString(ARG_CATEGORY_ID, categoryId);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        final String id = requireArguments().getString(ARG_CATEGORY_ID);
        category = SponsorBlockCategoryConfig.fromId(id);
        if (category == null) {
            throw new IllegalArgumentException("Unknown SponsorBlock category: " + id);
        }

        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(requireContext()));
        getPreferenceScreen().setTitle(category.titleResId);

        colorPreference = new ColorSwatchPreference(requireContext());
        colorPreference.setTitle(R.string.sponsor_block_category_color_title);
        colorPreference.setSummary(R.string.sponsor_block_category_color_summary);
        colorPreference.setColor(
                SponsorBlockCategoryRepository.getColor(requireContext(), category));
        colorPreference.setOnPreferenceClickListener(preference -> {
            showColorDialog();
            return true;
        });
        getPreferenceScreen().addPreference(colorPreference);

        behaviorPreference = new Preference(requireContext());
        behaviorPreference.setTitle(R.string.sponsor_block_category_behavior_title);
        behaviorPreference.setIconSpaceReserved(false);
        behaviorPreference.setSingleLineTitle(false);
        behaviorPreference.setEnabled(!category.isMarkerOnly());
        updateBehaviorSummary();
        behaviorPreference.setOnPreferenceClickListener(preference -> {
            if (!category.isMarkerOnly()) {
                showBehaviorDialog();
            }
            return true;
        });
        getPreferenceScreen().addPreference(behaviorPreference);
    }

    private void updateBehaviorSummary() {
        if (category.isMarkerOnly()) {
            behaviorPreference.setSummary(R.string.sponsor_block_behavior_highlight_summary);
            return;
        }
        behaviorPreference.setSummary(
                SponsorBlockCategoryRepository.getBehavior(requireContext(), category).titleResId);
    }

    private void showBehaviorDialog() {
        final SponsorBlockBehavior[] values = SponsorBlockBehavior.values();
        final String[] items = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            items[i] = getString(values[i].titleResId)
                    + "\n"
                    + getString(getBehaviorDescription(values[i]));
        }
        final int checked = SponsorBlockCategoryRepository.getBehavior(requireContext(), category)
                .ordinal();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.sponsor_block_category_behavior_title)
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    SponsorBlockCategoryRepository.setBehavior(requireContext(), category,
                            values[which]);
                    updateBehaviorSummary();
                    ServiceHelper.initServices(requireContext());
                    dialog.dismiss();
                })
                .show();
    }

    private int getBehaviorDescription(@NonNull final SponsorBlockBehavior behavior) {
        switch (behavior) {
            case MANUAL:
                return R.string.sponsor_block_behavior_manual_summary;
            case DONT_SKIP:
                return R.string.sponsor_block_behavior_dont_skip_summary;
            case SKIP:
            default:
                return R.string.sponsor_block_behavior_skip_summary;
        }
    }

    private void showColorDialog() {
        final View view = getLayoutInflater().inflate(
                R.layout.dialog_sponsor_block_color_picker, null);
        final View preview = view.findViewById(R.id.color_preview);
        final SeekBar red = view.findViewById(R.id.red);
        final SeekBar green = view.findViewById(R.id.green);
        final SeekBar blue = view.findViewById(R.id.blue);
        final EditText hex = view.findViewById(R.id.hex);
        final int[] selectedColor = {
                SponsorBlockCategoryRepository.getColor(requireContext(), category) | 0xFF000000
        };

        final Runnable updateFromSliders = () -> {
            if (updatingColorControls) {
                return;
            }
            updatingColorControls = true;
            selectedColor[0] = Color.rgb(red.getProgress(),
                    green.getProgress(), blue.getProgress());
            preview.setBackgroundColor(selectedColor[0]);
            hex.setError(null);
            hex.setText(String.format(Locale.US, "#%06X", 0xFFFFFF & selectedColor[0]));
            hex.setSelection(hex.length());
            updatingColorControls = false;
        };

        setColorControls(selectedColor[0], preview, red, green, blue, hex);
        final SeekBar.OnSeekBarChangeListener listener = new ColorSeekBarChangeListener(
                updateFromSliders);
        red.setOnSeekBarChangeListener(listener);
        green.setOnSeekBarChangeListener(listener);
        blue.setOnSeekBarChangeListener(listener);
        hex.addTextChangedListener(new HexColorTextWatcher(preview, red, green, blue, hex,
                selectedColor));

        final AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.sponsor_block_category_color_title)
                .setView(view)
                .setNeutralButton(R.string.sponsor_block_color_default,
                        (ignoredDialog, which) -> clearColorOverride())
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    final Integer parsedColor = parseHexColor(hex);
                    if (parsedColor == null) {
                        return;
                    }
                    saveColor(parsedColor);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    @Nullable
    private Integer parseHexColor(@NonNull final EditText hex) {
        final String value = hex.getText().toString();
        if (!HEX_COLOR_PATTERN.matcher(value).matches()) {
            hex.setError(getString(R.string.sponsor_block_color_invalid));
            return null;
        }
        final String normalized = value.startsWith("#") ? value.substring(1) : value;
        return 0xFF000000 | Integer.parseInt(normalized, 16);
    }

    private void setColorControls(final int color,
                                  @NonNull final View preview,
                                  @NonNull final SeekBar red,
                                  @NonNull final SeekBar green,
                                  @NonNull final SeekBar blue,
                                  @NonNull final EditText hex) {
        updatingColorControls = true;
        red.setProgress(Color.red(color));
        green.setProgress(Color.green(color));
        blue.setProgress(Color.blue(color));
        preview.setBackgroundColor(color);
        hex.setError(null);
        hex.setText(String.format(Locale.US, "#%06X", 0xFFFFFF & color));
        hex.setSelection(hex.length());
        updatingColorControls = false;
    }

    private void saveColor(final int color) {
        SponsorBlockCategoryRepository.setColor(requireContext(), category,
                color | 0xFF000000);
        colorPreference.setColor(
                SponsorBlockCategoryRepository.getColor(requireContext(), category));
        ServiceHelper.initServices(requireContext());
    }

    private void clearColorOverride() {
        SponsorBlockCategoryRepository.clearColorOverride(requireContext(), category);
        colorPreference.setColor(ContextCompat.getColor(requireContext(),
                category.defaultColorResId));
        ServiceHelper.initServices(requireContext());
    }

    private final class HexColorTextWatcher implements TextWatcher {
        private final View preview;
        private final SeekBar red;
        private final SeekBar green;
        private final SeekBar blue;
        private final EditText hex;
        private final int[] selectedColor;

        private HexColorTextWatcher(@NonNull final View preview,
                                    @NonNull final SeekBar red,
                                    @NonNull final SeekBar green,
                                    @NonNull final SeekBar blue,
                                    @NonNull final EditText hex,
                                    @NonNull final int[] selectedColor) {
            this.preview = preview;
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.hex = hex;
            this.selectedColor = selectedColor;
        }

        @Override
        public void beforeTextChanged(final CharSequence s, final int start,
                                      final int count, final int after) {
        }

        @Override
        public void onTextChanged(final CharSequence s, final int start,
                                  final int before, final int count) {
            if (updatingColorControls) {
                return;
            }
            final String value = s.toString();
            if (value.isEmpty() || "#".equals(value)) {
                hex.setError(null);
                return;
            }
            if (!HEX_COLOR_PATTERN.matcher(value).matches()) {
                if (value.length() >= 6) {
                    hex.setError(getString(R.string.sponsor_block_color_invalid));
                }
                return;
            }

            final String normalized = value.startsWith("#") ? value.substring(1) : value;
            final int parsedColor = 0xFF000000 | Integer.parseInt(normalized, 16);
            selectedColor[0] = parsedColor;
            setColorControls(parsedColor, preview, red, green, blue, hex);
        }

        @Override
        public void afterTextChanged(final Editable s) {
        }
    }

    private static final class ColorSeekBarChangeListener
            implements SeekBar.OnSeekBarChangeListener {
        private final Runnable onUserChange;

        private ColorSeekBarChangeListener(@NonNull final Runnable onUserChange) {
            this.onUserChange = onUserChange;
        }

        @Override
        public void onProgressChanged(final SeekBar seekBar, final int progress,
                                      final boolean fromUser) {
            if (fromUser) {
                onUserChange.run();
            }
        }

        @Override
        public void onStartTrackingTouch(final SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(final SeekBar seekBar) {
        }
    }
}
