/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.equalizer;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.schabi.newpipe.R;
import org.schabi.newpipe.player.Player;

import java.util.Locale;

/**
 * Shared responsive equalizer editor used by player controls and settings.
 */
public final class EqualizerDialog {
    private static final EqualizerPreset[] DISPLAYED_PRESETS = {
        EqualizerPreset.FLAT,
        EqualizerPreset.BASS_BOOST,
        EqualizerPreset.VOCAL,
        EqualizerPreset.ACOUSTIC,
        EqualizerPreset.ROCK,
        EqualizerPreset.CUSTOM
    };

    private EqualizerDialog() {
    }

    public interface StateAdapter {
        @NonNull
        EqualizerState getState();

        boolean isAvailable();

        boolean isOperational();

        boolean appliesLive();

        void preview(@NonNull EqualizerState state);

        void commit(@NonNull EqualizerState state);
    }

    @NonNull
    public static StateAdapter forPlayer(@NonNull final Player player) {
        return new StateAdapter() {
            @NonNull
            @Override
            public EqualizerState getState() {
                return player.getEqualizerState();
            }

            @Override
            public boolean isAvailable() {
                return player.isEqualizerAvailable();
            }

            @Override
            public boolean isOperational() {
                return player.isEqualizerOperational();
            }

            @Override
            public boolean appliesLive() {
                return true;
            }

            @Override
            public void preview(@NonNull final EqualizerState state) {
                player.previewEqualizerState(state);
            }

            @Override
            public void commit(@NonNull final EqualizerState state) {
                player.updateEqualizerState(state);
            }
        };
    }

    @NonNull
    public static StateAdapter forPreferences(@NonNull final Context context) {
        final EqualizerPreferences preferences = new EqualizerPreferences(context);
        final EqualizerState initialState = preferences.load();
        final boolean available = new AndroidEqualizerEngineFactory().isAvailable();
        return new StateAdapter() {
            private EqualizerState state = initialState;

            @NonNull
            @Override
            public EqualizerState getState() {
                return state;
            }

            @Override
            public boolean isAvailable() {
                return available;
            }

            @Override
            public boolean isOperational() {
                return false;
            }

            @Override
            public boolean appliesLive() {
                return false;
            }

            @Override
            public void preview(@NonNull final EqualizerState newState) {
                state = newState;
            }

            @Override
            public void commit(@NonNull final EqualizerState newState) {
                state = newState;
                preferences.save(state);
            }
        };
    }

    public static void show(@NonNull final Context context,
                            @NonNull final StateAdapter adapter) {
        show(context, adapter, () -> {
        });
    }

    public static void show(@NonNull final Context context,
                            @NonNull final StateAdapter adapter,
                            @NonNull final Runnable onDismiss) {
        final Editor editor = new Editor(context, adapter);
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.equalizer)
                .setView(editor.getView())
                .setPositiveButton(R.string.close, null)
                .setOnDismissListener(dialog -> {
                    adapter.commit(editor.getState());
                    onDismiss.run();
                })
                .show();
    }

    @NonNull
    public static String getPresetDisplayName(@NonNull final Context context,
                                               @NonNull final EqualizerPreset preset) {
        switch (preset) {
            case FLAT:
                return context.getString(R.string.equalizer_preset_flat);
            case BASS_BOOST:
                return context.getString(R.string.equalizer_preset_bass_boost);
            case VOCAL:
                return context.getString(R.string.equalizer_preset_vocal);
            case ACOUSTIC:
                return context.getString(R.string.equalizer_preset_acoustic);
            case ROCK:
                return context.getString(R.string.equalizer_preset_rock);
            case CUSTOM:
            default:
                return context.getString(R.string.equalizer_preset_custom);
        }
    }

    private static final class Editor {
        @NonNull
        private final Context context;
        @NonNull
        private final StateAdapter adapter;
        @NonNull
        private final LinearLayout content;
        @NonNull
        private final Spinner presetSpinner;
        @NonNull
        private final TextView statusText;
        @NonNull
        private final TextView headroomText;
        @NonNull
        private final SeekBar[] seekBars = new SeekBar[EqualizerState.BAND_COUNT];
        @NonNull
        private final TextView[] gainLabels = new TextView[EqualizerState.BAND_COUNT];
        @NonNull
        private EqualizerState state;
        private boolean updatingControls;

        Editor(@NonNull final Context context, @NonNull final StateAdapter adapter) {
            this.context = context;
            this.adapter = adapter;
            state = adapter.getState();

            content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            final int padding = dp(20);
            content.setPadding(padding, dp(4), padding, dp(8));

            final SwitchMaterial enabledSwitch = new SwitchMaterial(context);
            enabledSwitch.setText(R.string.equalizer_enable);
            enabledSwitch.setChecked(state.isEnabled());
            enabledSwitch.setEnabled(adapter.isAvailable());
            content.addView(enabledSwitch, matchWrap());

            statusText = new TextView(context);
            statusText.setPadding(0, dp(4), 0, dp(12));
            content.addView(statusText, matchWrap());

            final TextView presetLabel = sectionLabel(R.string.equalizer_preset);
            content.addView(presetLabel, matchWrap());

            presetSpinner = new Spinner(context);
            final String[] presetNames = new String[DISPLAYED_PRESETS.length];
            for (int index = 0; index < DISPLAYED_PRESETS.length; index++) {
                presetNames[index] = getPresetDisplayName(context, DISPLAYED_PRESETS[index]);
            }
            final ArrayAdapter<String> presetAdapter = new ArrayAdapter<>(
                    context, android.R.layout.simple_spinner_dropdown_item, presetNames);
            presetSpinner.setAdapter(presetAdapter);
            content.addView(presetSpinner, matchWrap());

            final TextView curveDescription = new TextView(context);
            curveDescription.setText(R.string.equalizer_curve_description);
            curveDescription.setPadding(0, dp(12), 0, dp(8));
            content.addView(curveDescription, matchWrap());

            for (int band = 0; band < EqualizerState.BAND_COUNT; band++) {
                addBand(band);
            }

            headroomText = new TextView(context);
            headroomText.setPadding(0, dp(12), 0, 0);
            content.addView(headroomText, matchWrap());

            enabledSwitch.setOnCheckedChangeListener((button, checked) -> {
                state = state.withEnabled(checked);
                adapter.commit(state);
                refreshStatus();
            });
            presetSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
                if (updatingControls) {
                    return;
                }
                final EqualizerPreset preset = DISPLAYED_PRESETS[position];
                if (preset == EqualizerPreset.CUSTOM) {
                    return;
                }
                state = state.withPreset(preset);
                updateBandControls();
                adapter.commit(state);
                refreshStatus();
            }));

            updateBandControls();
            refreshStatus();
        }

        @NonNull
        View getView() {
            final android.widget.ScrollView scrollView =
                    new android.widget.ScrollView(context);
            scrollView.addView(content);
            return scrollView;
        }

        @NonNull
        EqualizerState getState() {
            return state;
        }

        private void addBand(final int band) {
            final LinearLayout header = new LinearLayout(context);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setPadding(0, dp(8), 0, 0);

            final TextView frequency = new TextView(context);
            frequency.setText(formatFrequency(EqualizerState.BAND_FREQUENCIES_HZ[band]));
            frequency.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            header.addView(frequency, weightedWrap());

            gainLabels[band] = new TextView(context);
            header.addView(gainLabels[band], wrapWrap());
            content.addView(header, matchWrap());

            final SeekBar seekBar = new SeekBar(context);
            seekBar.setMax(EqualizerState.MAX_GAIN_STEP - EqualizerState.MIN_GAIN_STEP);
            seekBar.setKeyProgressIncrement(1);
            seekBar.setContentDescription(formatFrequency(
                    EqualizerState.BAND_FREQUENCIES_HZ[band]));
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(final SeekBar bar,
                                              final int progress,
                                              final boolean fromUser) {
                    if (!fromUser || updatingControls) {
                        return;
                    }
                    final int gain = progress + EqualizerState.MIN_GAIN_STEP;
                    state = state.withBandGain(band, gain);
                    gainLabels[band].setText(formatGain(gain));
                    selectPreset(EqualizerPreset.CUSTOM);
                    adapter.preview(state);
                    refreshStatus();
                }

                @Override
                public void onStartTrackingTouch(final SeekBar bar) {
                }

                @Override
                public void onStopTrackingTouch(final SeekBar bar) {
                    adapter.commit(state);
                }
            });
            seekBars[band] = seekBar;
            content.addView(seekBar, matchWrap());
        }

        private void updateBandControls() {
            updatingControls = true;
            final int[] gains = state.getGains();
            for (int band = 0; band < gains.length; band++) {
                seekBars[band].setProgress(gains[band] - EqualizerState.MIN_GAIN_STEP);
                gainLabels[band].setText(formatGain(gains[band]));
            }
            selectPreset(state.getPreset());
            updatingControls = false;
            refreshHeadroom();
        }

        private void selectPreset(@NonNull final EqualizerPreset preset) {
            for (int index = 0; index < DISPLAYED_PRESETS.length; index++) {
                if (DISPLAYED_PRESETS[index] == preset) {
                    updatingControls = true;
                    presetSpinner.setSelection(index);
                    updatingControls = false;
                    return;
                }
            }
        }

        private void refreshStatus() {
            if (!adapter.isAvailable()) {
                statusText.setText(R.string.equalizer_unavailable);
            } else if (!state.isEnabled()) {
                statusText.setText(R.string.equalizer_disabled);
            } else if (!adapter.appliesLive()) {
                statusText.setText(R.string.equalizer_applies_next_playback);
            } else if (adapter.isOperational()) {
                statusText.setText(R.string.equalizer_active);
            } else {
                statusText.setText(R.string.equalizer_waiting_for_audio_session);
            }
            refreshHeadroom();
        }

        private void refreshHeadroom() {
            final int maximumGainStep =
                    java.util.Arrays.stream(state.getGains()).max().orElse(0);
            final double headroom =
                    state.isEnabled() ? Math.max(0, maximumGainStep) / 2.0 : 0.0;
            headroomText.setText(context.getString(
                    R.string.equalizer_headroom, headroom));
        }

        @NonNull
        private TextView sectionLabel(final int textResource) {
            final TextView view = new TextView(context);
            view.setText(textResource);
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            return view;
        }

        @NonNull
        private String formatFrequency(final int frequencyHertz) {
            if (frequencyHertz >= 1_000) {
                return context.getString(
                        R.string.equalizer_frequency_khz, frequencyHertz / 1_000);
            }
            return context.getString(R.string.equalizer_frequency_hz, frequencyHertz);
        }

        @NonNull
        private String formatGain(final int gainStep) {
            return String.format(Locale.getDefault(), "%+.1f dB", gainStep / 2.0);
        }

        private int dp(final int value) {
            return Math.round(value * context.getResources().getDisplayMetrics().density);
        }

        @NonNull
        private LinearLayout.LayoutParams matchWrap() {
            return new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
        }

        @NonNull
        private LinearLayout.LayoutParams wrapWrap() {
            return new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
        }

        @NonNull
        private LinearLayout.LayoutParams weightedWrap() {
            return new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        }
    }

    private interface PositionConsumer {
        void accept(int position);
    }

    private static final class SimpleItemSelectedListener
            implements android.widget.AdapterView.OnItemSelectedListener {
        @NonNull
        private final PositionConsumer consumer;

        SimpleItemSelectedListener(@NonNull final PositionConsumer consumer) {
            this.consumer = consumer;
        }

        @Override
        public void onItemSelected(final android.widget.AdapterView<?> parent,
                                   final View view,
                                   final int position,
                                   final long id) {
            consumer.accept(position);
        }

        @Override
        public void onNothingSelected(final android.widget.AdapterView<?> parent) {
        }
    }
}
