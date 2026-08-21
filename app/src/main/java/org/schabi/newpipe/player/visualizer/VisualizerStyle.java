package org.schabi.newpipe.player.visualizer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Stable visualizer style values stored in the player's shared preferences. */
public enum VisualizerStyle {
    CENTERED_BARS("centered_bars"),
    CLASSIC_BARS("classic_bars"),
    MIRRORED_BARS("mirrored_bars"),
    WAVEFORM("waveform"),
    FILLED_WAVEFORM("filled_waveform"),
    OSCILLOSCOPE("oscilloscope"),
    SPECTRUM_LINE("spectrum_line"),
    MOUNTAIN("mountain"),
    CIRCULAR_SPECTRUM("circular_spectrum"),
    RADIAL_WAVEFORM("radial_waveform"),
    EQUALIZER_BLOCKS("equalizer_blocks"),
    PARTICLES("particles"),
    PULSE_RINGS("pulse_rings"),
    VU_METERS("vu_meters"),
    NEON_DOTS("neon_dots");

    @NonNull
    private final String preferenceValue;

    VisualizerStyle(@NonNull final String preferenceValue) {
        this.preferenceValue = preferenceValue;
    }

    @NonNull
    public String getPreferenceValue() {
        return preferenceValue;
    }

    @NonNull
    public static VisualizerStyle fromPreferenceValue(@Nullable final String value) {
        for (final VisualizerStyle style : values()) {
            if (style.preferenceValue.equals(value)) {
                return style;
            }
        }
        return CENTERED_BARS;
    }
}
